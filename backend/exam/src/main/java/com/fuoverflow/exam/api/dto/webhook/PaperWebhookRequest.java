package com.fuoverflow.exam.api.dto.webhook;

import java.time.Instant;

/** Envelope of one paper delivery. {@code eventId} is the sender's idempotency key. */
public record PaperWebhookRequest(
        String eventId,
        String eventType,
        Instant sentAt,
        PaperPayload paper
) {
}
