package com.fuoverflow.exam.api.dto.webhook;

import java.util.List;

/**
 * The outcome of one webhook request. A single-paper body returns a one-element {@code results},
 * so senders read the same shape whichever body they post.
 */
public record WebhookBatchReceiptResponse(
        int accepted,
        int duplicate,
        int rejected,
        List<WebhookPaperReceipt> results
) {
}
