package com.fuoverflow.payment.api;

import org.junit.jupiter.api.Test;
import vn.payos.model.webhooks.WebhookData;

import static org.assertj.core.api.Assertions.assertThat;

class PayOSWebhookControllerTest {

    @Test
    void deriveProviderEventId_shouldUseReferenceWhenPresent() {
        WebhookData webhookData = new WebhookData();
        webhookData.setReference("ref-123");
        webhookData.setOrderCode(1001L);
        webhookData.setCode("00");

        String eventId = PayOSWebhookController.deriveProviderEventId(webhookData);

        assertThat(eventId).isEqualTo("ref-123");
    }

    @Test
    void deriveProviderEventId_shouldFallbackToOrderCodeAndCode() {
        WebhookData webhookData = new WebhookData();
        webhookData.setReference("");
        webhookData.setOrderCode(1001L);
        webhookData.setCode("00");

        String eventId = PayOSWebhookController.deriveProviderEventId(webhookData);

        assertThat(eventId).isEqualTo("payos-order-1001-code-00");
    }

    @Test
    void invalidEventId_shouldBeStableForSameBody() {
        String body = "{\"data\":{\"orderCode\":1001}}";

        String first = PayOSWebhookController.invalidEventId(body);
        String second = PayOSWebhookController.invalidEventId(body);

        assertThat(first).isEqualTo(second);
        assertThat(first).startsWith("invalid-");
    }
}
