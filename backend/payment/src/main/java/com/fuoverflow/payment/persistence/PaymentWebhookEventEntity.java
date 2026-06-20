package com.fuoverflow.payment.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_webhook_events")
public class PaymentWebhookEventEntity {
    @Id
    private UUID id;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Column(name = "provider_event_id", nullable = false, length = 255)
    private String providerEventId;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;

    @Column(name = "processed_at")
    private Instant processedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentWebhookEventEntity() {
    }

    public static PaymentWebhookEventEntity create(String provider, String providerEventId, String eventType,
                                                    String payloadJson, boolean signatureValid) {
        PaymentWebhookEventEntity e = new PaymentWebhookEventEntity();
        e.id = UUID.randomUUID();
        e.provider = provider;
        e.providerEventId = providerEventId;
        e.eventType = eventType;
        e.payloadJson = payloadJson;
        e.signatureValid = signatureValid;
        return e;
    }

    public void markProcessed(Instant processedAt) {
        this.processedAt = processedAt;
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public boolean isSignatureValid() {
        return signatureValid;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
