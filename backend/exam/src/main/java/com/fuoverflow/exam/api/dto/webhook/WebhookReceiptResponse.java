package com.fuoverflow.exam.api.dto.webhook;

import java.util.UUID;

public record WebhookReceiptResponse(UUID receiptId, String status, boolean duplicate) {
}
