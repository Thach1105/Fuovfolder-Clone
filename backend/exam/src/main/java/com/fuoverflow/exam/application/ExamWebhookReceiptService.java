package com.fuoverflow.exam.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.PayloadTooLargeException;
import com.fuoverflow.exam.api.dto.webhook.PaperWebhookRequest;
import com.fuoverflow.exam.api.dto.webhook.WebhookReceiptResponse;
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
 * {@link ExamWebhookPayloadReader}, so the stored row is always the sender's original bytes while
 * everything downstream sees one shape.
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
    public WebhookReceiptResponse receive(String clientId, String rawBody, String signatureHeader) {
        long size = rawBody == null ? 0 : rawBody.getBytes(StandardCharsets.UTF_8).length;
        if (size > properties.maxPayloadBytesOrDefault()) {
            throw new PayloadTooLargeException("WEBHOOK_TOO_LARGE",
                    "Body " + size + " byte vượt giới hạn " + properties.maxPayloadBytesOrDefault() + ".");
        }

        signatureVerifier.verify(clientId, rawBody, signatureHeader);

        PaperWebhookRequest request = payloadReader.read(rawBody);
        String normalizedClientId = clientId.trim();
        String eventId = request.eventId() == null ? null : request.eventId().trim();

        Optional<ExamWebhookEventEntity> existing = eventId == null
                ? Optional.empty()
                : eventRepository.findByClientIdAndEventId(normalizedClientId, eventId);
        if (existing.isPresent()) {
            ExamWebhookEventEntity event = existing.get();
            log.info("Exam paper webhook duplicate: client={} eventId={} status={}",
                    normalizedClientId, eventId, event.getStatus());
            return new WebhookReceiptResponse(event.getId(), wireStatus(event.getStatus()), true);
        }

        payloadValidator.validate(request);

        ExamWebhookEventEntity event = ExamWebhookEventEntity.received(
                UUID.randomUUID(),
                normalizedClientId,
                eventId,
                request.eventType() == null ? "exam.paper.upserted" : request.eventType().trim(),
                rawBody,
                Sha256.hexUtf8(rawBody),
                true,
                Instant.now());
        ExamWebhookEventEntity saved = eventRepository.save(event);
        log.info("Exam paper webhook queued: client={} eventId={} receiptId={} bytes={}",
                normalizedClientId, eventId, saved.getId(), size);
        return new WebhookReceiptResponse(saved.getId(), "queued", false);
    }

    private static String wireStatus(String storedStatus) {
        return switch (storedStatus) {
            case ExamWebhookEventEntity.STATUS_DONE -> "done";
            case ExamWebhookEventEntity.STATUS_FAILED -> "failed";
            default -> "queued";
        };
    }
}
