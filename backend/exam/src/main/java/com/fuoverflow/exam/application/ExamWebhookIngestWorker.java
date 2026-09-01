package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuoverflow.common.exception.ApiException;
import com.fuoverflow.exam.api.dto.webhook.PaperWebhookRequest;
import com.fuoverflow.exam.config.ExamWebhookProperties;
import com.fuoverflow.exam.domain.IngestPaper;
import com.fuoverflow.exam.persistence.ExamWebhookEventEntity;
import com.fuoverflow.exam.persistence.ExamWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Drains the webhook inbox: decode, upload, build the paper.
 *
 * <p>This work is deliberately out of the request path — fifty images plus a resource download runs
 * far longer than a sender will wait, and a timeout would make it retry and duplicate. Each event
 * is handled in its own try/catch so one poisoned delivery cannot stall the queue, and a payload
 * error fails immediately rather than burning five retries on a body that will never parse.
 */
@Component
public class ExamWebhookIngestWorker {
    private static final Logger log = LoggerFactory.getLogger(ExamWebhookIngestWorker.class);
    /** Fields that carry base64 image bytes: canonical uses one name, EOS the other. */
    private static final List<String> IMAGE_BYTE_FIELDS = List.of("contentBase64", "ImageData");

    private final ExamWebhookEventRepository eventRepository;
    private final ExamWebhookPayloadValidator payloadValidator;
    private final ExamWebhookPayloadReader payloadReader;
    private final ExamPaperIngestService ingestService;
    private final ExamWebhookProperties properties;
    private final ObjectMapper objectMapper;

    public ExamWebhookIngestWorker(
            ExamWebhookEventRepository eventRepository,
            ExamWebhookPayloadValidator payloadValidator,
            ExamWebhookPayloadReader payloadReader,
            ExamPaperIngestService ingestService,
            ExamWebhookProperties properties,
            ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.payloadValidator = payloadValidator;
        this.payloadReader = payloadReader;
        this.ingestService = ingestService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${fuexam.exam.webhook.poll-interval-ms:30000}")
    public void processPending() {
        List<ExamWebhookEventEntity> due = eventRepository
                .findTop20ByStatusAndAvailableAtBeforeOrderByAvailableAtAsc(
                        ExamWebhookEventEntity.STATUS_PENDING, Instant.now());
        for (ExamWebhookEventEntity event : due) {
            try {
                process(event);
            } catch (RuntimeException e) {
                log.error("Unexpected failure handling exam webhook event {}", event.getId(), e);
            }
        }
    }

    private void process(ExamWebhookEventEntity event) {
        event.markProcessing();
        eventRepository.save(event);

        PaperWebhookRequest request;
        try {
            request = payloadReader.read(event.getPayloadJson());
        } catch (Exception e) {
            // A body that will not parse now will not parse on any retry either.
            event.markFailed("WEBHOOK_PAYLOAD_INVALID", "Body không phải JSON hợp lệ.", Instant.now());
            eventRepository.save(event);
            log.warn("Exam webhook event {} has an unparseable payload", event.getId());
            return;
        }

        try {
            IngestPaper paper = payloadValidator.validate(request);

            ExamPaperIngestService.IngestOutcome outcome =
                    ingestService.ingest(paper, "webhook:" + event.getClientId());

            event.markDone(outcome.paperId(), Instant.now());
            event.setPayloadJson(withoutImageBytes(event.getPayloadJson()));
            eventRepository.save(event);
            log.info("Exam webhook event {} ingested: paper={} outcome={}",
                    event.getId(), outcome.paperId(), outcome.outcome());
        } catch (ApiException e) {
            // A rejected payload will be rejected identically on every retry.
            event.markFailed(e.code(), e.getMessage(), Instant.now());
            eventRepository.save(event);
            log.warn("Exam webhook event {} rejected: {} {}", event.getId(), e.code(), e.getMessage());
        } catch (Exception e) {
            handleTransientFailure(event, e);
        }
    }

    private void handleTransientFailure(ExamWebhookEventEntity event, Exception cause) {
        if (event.getAttemptCount() >= properties.maxAttemptsOrDefault()) {
            event.markFailed("WEBHOOK_INGEST_FAILED", cause.getMessage(), Instant.now());
            log.error("Exam webhook event {} failed after {} attempts",
                    event.getId(), event.getAttemptCount(), cause);
        } else {
            long delaySeconds = 60L << (event.getAttemptCount() - 1);
            event.retryAt(Instant.now().plusSeconds(delaySeconds));
            log.warn("Exam webhook event {} retry {} in {}s: {}",
                    event.getId(), event.getAttemptCount(), delaySeconds, cause.getMessage());
        }
        eventRepository.save(event);
    }

    /**
     * Base64 PNG does not compress, so keeping it would leave megabyte-sized rows behind for data
     * already living in object storage. The {@code sha256} values stay, which is what an audit
     * actually needs.
     */
    private String withoutImageBytes(String payloadJson) {
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            stripContent(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Could not strip image bytes from webhook payload: {}", e.getMessage());
            return payloadJson;
        }
    }

    private static void stripContent(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            for (String field : IMAGE_BYTE_FIELDS) {
                if (object.has(field)) {
                    object.putNull(field);
                }
            }
            object.forEach(ExamWebhookIngestWorker::stripContent);
        } else if (node.isArray()) {
            node.forEach(ExamWebhookIngestWorker::stripContent);
        }
    }
}
