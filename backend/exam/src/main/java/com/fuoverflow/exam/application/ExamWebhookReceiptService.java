package com.fuoverflow.exam.application;

import com.fuoverflow.common.exception.ApiException;
import com.fuoverflow.common.exception.PayloadTooLargeException;
import com.fuoverflow.exam.api.dto.webhook.PaperWebhookRequest;
import com.fuoverflow.exam.api.dto.webhook.WebhookBatchReceiptResponse;
import com.fuoverflow.exam.api.dto.webhook.WebhookPaperReceipt;
import com.fuoverflow.exam.config.ExamWebhookProperties;
import com.fuoverflow.exam.persistence.ExamWebhookEventEntity;
import com.fuoverflow.exam.persistence.ExamWebhookEventRepository;
import com.fuoverflow.exam.support.Sha256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Accepts a paper delivery and parks it for the ingest worker.
 *
 * <p>The order of the checks is the design: size before parsing so an oversized body never reaches
 * Jackson, signature before the database so an unauthenticated caller cannot make us write, and
 * full payload validation before the row is stored so an unprocessable delivery fails as a 400 the
 * sender can act on instead of a queued row that fails silently later.
 *
 * <p>Both accepted body shapes — the canonical envelope and a raw EOS exam file — come in through
 * {@link ExamWebhookPayloadReader}, which also splits a {@code papers[]} batch, so one request
 * becomes one queued row per paper and everything downstream still sees a single paper.
 */
@Service
public class ExamWebhookReceiptService {
    private static final Logger log = LoggerFactory.getLogger(ExamWebhookReceiptService.class);

    private final ExamWebhookSignatureVerifier signatureVerifier;
    private final ExamWebhookPayloadValidator payloadValidator;
    private final ExamWebhookEventRepository eventRepository;
    private final ExamWebhookProperties properties;
    private final ExamWebhookPayloadReader payloadReader;

    public ExamWebhookReceiptService(
            ExamWebhookSignatureVerifier signatureVerifier,
            ExamWebhookPayloadValidator payloadValidator,
            ExamWebhookEventRepository eventRepository,
            ExamWebhookProperties properties,
            ExamWebhookPayloadReader payloadReader) {
        this.signatureVerifier = signatureVerifier;
        this.payloadValidator = payloadValidator;
        this.eventRepository = eventRepository;
        this.properties = properties;
        this.payloadReader = payloadReader;
    }

    @Transactional
    public WebhookBatchReceiptResponse receive(String clientId, String rawBody, String signatureHeader) {
        long size = rawBody == null ? 0 : rawBody.getBytes(StandardCharsets.UTF_8).length;
        if (size > properties.maxPayloadBytesOrDefault()) {
            throw new PayloadTooLargeException("WEBHOOK_TOO_LARGE",
                    "Body " + size + " byte vượt giới hạn " + properties.maxPayloadBytesOrDefault() + ".");
        }

        signatureVerifier.verify(clientId, rawBody, signatureHeader);

        List<ExamWebhookPayloadReader.DeliveredPaper> delivered =
                payloadReader.readAll(rawBody, properties.maxPapersPerBatchOrDefault());
        String normalizedClientId = clientId.trim();

        List<WebhookPaperReceipt> results = new ArrayList<>(delivered.size());
        int accepted = 0;
        int duplicates = 0;
        int rejected = 0;
        ApiException firstRejection = null;

        for (int index = 0; index < delivered.size(); index++) {
            PaperWebhookRequest request = delivered.get(index).request();
            String examCode = request.paper() == null ? null : request.paper().examCode();
            String eventId = request.eventId() == null ? null : request.eventId().trim();

            Optional<ExamWebhookEventEntity> existing = eventId == null
                    ? Optional.empty()
                    : eventRepository.findByClientIdAndEventId(normalizedClientId, eventId);
            if (existing.isPresent()) {
                ExamWebhookEventEntity event = existing.get();
                log.info("Exam paper webhook duplicate: client={} eventId={} status={}",
                        normalizedClientId, eventId, event.getStatus());
                results.add(new WebhookPaperReceipt(index, examCode, event.getId(),
                        wireStatus(event.getStatus()), true, null, null));
                duplicates++;
                continue;
            }

            try {
                payloadValidator.validate(request);
            } catch (ApiException e) {
                log.warn("Exam paper webhook paper {} rejected: {} {}", index, e.code(), e.getMessage());
                results.add(new WebhookPaperReceipt(index, examCode, null, "rejected", false,
                        e.code(), e.getMessage()));
                rejected++;
                if (firstRejection == null) {
                    firstRejection = e;
                }
                continue;
            }

            String payloadJson = delivered.get(index).payloadJson();
            ExamWebhookEventEntity saved = eventRepository.save(ExamWebhookEventEntity.received(
                    UUID.randomUUID(),
                    normalizedClientId,
                    eventId,
                    request.eventType() == null ? "exam.paper.upserted" : request.eventType().trim(),
                    payloadJson,
                    Sha256.hexUtf8(payloadJson),
                    true,
                    Instant.now()));
            log.info("Exam paper webhook queued: client={} eventId={} receiptId={}",
                    normalizedClientId, eventId, saved.getId());
            results.add(new WebhookPaperReceipt(index, examCode, saved.getId(), "queued", false,
                    null, null));
            accepted++;
        }

        if (accepted == 0 && duplicates == 0) {
            // Nothing landed: a sender of a single paper must still see the exact failure it
            // sees today rather than a 200 carrying an error buried in a results array.
            throw firstRejection;
        }
        log.info("Exam paper webhook received: client={} accepted={} duplicate={} rejected={} bytes={}",
                normalizedClientId, accepted, duplicates, rejected, size);
        return new WebhookBatchReceiptResponse(accepted, duplicates, rejected, results);
    }

    private static String wireStatus(String storedStatus) {
        return switch (storedStatus) {
            case ExamWebhookEventEntity.STATUS_DONE -> "done";
            case ExamWebhookEventEntity.STATUS_FAILED -> "failed";
            default -> "queued";
        };
    }
}
