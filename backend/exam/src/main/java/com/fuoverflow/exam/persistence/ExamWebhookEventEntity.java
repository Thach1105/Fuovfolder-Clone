package com.fuoverflow.exam.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One received paper webhook delivery. The raw body is kept so a transient failure can be retried
 * without asking the sender to redeliver; once processing succeeds the worker rewrites the payload
 * with the base64 image content removed, because those bytes are already in object storage.
 */
@Entity
@Table(name = "exam_webhook_events")
public class ExamWebhookEventEntity {
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_FAILED = "failed";

    @Id
    private UUID id;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "event_id", nullable = false, length = 120)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "payload_sha256", nullable = false, length = 64)
    private String payloadSha256;

    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "paper_id")
    private UUID paperId;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public String getClientId() { return clientId; }
    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getPayloadJson() { return payloadJson; }
    public String getPayloadSha256() { return payloadSha256; }
    public boolean isSignatureValid() { return signatureValid; }
    public String getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public UUID getPaperId() { return paperId; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getAvailableAt() { return availableAt; }
    public Instant getProcessedAt() { return processedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public void markProcessing() {
        this.status = STATUS_PROCESSING;
        this.attemptCount++;
    }

    public void markDone(UUID paperId, Instant at) {
        this.status = STATUS_DONE;
        this.paperId = paperId;
        this.processedAt = at;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markFailed(String errorCode, String errorMessage, Instant at) {
        this.status = STATUS_FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.processedAt = at;
    }

    /** Back to the queue, keeping the attempt count so the backoff can grow. */
    public void retryAt(Instant available) {
        this.status = STATUS_PENDING;
        this.availableAt = available;
        this.processedAt = null;
    }

    public static ExamWebhookEventEntity received(
            UUID id, String clientId, String eventId, String eventType,
            String payloadJson, String payloadSha256, boolean signatureValid, Instant now) {
        ExamWebhookEventEntity e = new ExamWebhookEventEntity();
        e.id = id;
        e.clientId = clientId;
        e.eventId = eventId;
        e.eventType = eventType;
        e.payloadJson = payloadJson;
        e.payloadSha256 = payloadSha256;
        e.signatureValid = signatureValid;
        e.status = STATUS_PENDING;
        e.attemptCount = 0;
        e.availableAt = now;
        e.createdAt = now;
        return e;
    }
}
