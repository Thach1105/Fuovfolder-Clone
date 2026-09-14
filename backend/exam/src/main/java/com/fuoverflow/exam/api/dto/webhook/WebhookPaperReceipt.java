package com.fuoverflow.exam.api.dto.webhook;

import java.util.UUID;

/**
 * The outcome for one paper of a delivery. {@code receiptId} is null for a rejected paper, and
 * {@code errorCode}/{@code errorMessage} are null for every other status.
 */
public record WebhookPaperReceipt(
        int index,
        String examCode,
        UUID receiptId,
        String status,
        boolean duplicate,
        String errorCode,
        String errorMessage
) {
}
