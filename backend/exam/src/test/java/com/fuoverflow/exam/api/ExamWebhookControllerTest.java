package com.fuoverflow.exam.api;

import com.fuoverflow.exam.api.dto.webhook.WebhookBatchReceiptResponse;
import com.fuoverflow.exam.api.dto.webhook.WebhookPaperReceipt;
import com.fuoverflow.exam.application.ExamWebhookReceiptService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamWebhookControllerTest {

    @Mock private ExamWebhookReceiptService receiptService;
    @InjectMocks private ExamWebhookController controller;

    @Test
    void acceptsWithTwoOhTwoWhenAtLeastOnePaperIsNew() {
        when(receiptService.receive(any(), any(), any())).thenReturn(
                new WebhookBatchReceiptResponse(1, 1, 0, List.of(
                        queued(0), duplicate(1))));

        assertEquals(HttpStatus.ACCEPTED,
                controller.receivePaper("eos-crawler", "sig", "{}").getStatusCode());
    }

    @Test
    void returnsTwoHundredWhenEveryPaperWasAlreadySeen() {
        when(receiptService.receive(any(), any(), any())).thenReturn(
                new WebhookBatchReceiptResponse(0, 2, 0, List.of(
                        duplicate(0), duplicate(1))));

        assertEquals(HttpStatus.OK,
                controller.receivePaper("eos-crawler", "sig", "{}").getStatusCode());
    }

    @Test
    void returnsTwoHundredWhenADuplicateBatchAlsoCarriesARejection() {
        when(receiptService.receive(any(), any(), any())).thenReturn(
                new WebhookBatchReceiptResponse(0, 1, 1, List.of(
                        duplicate(0),
                        new WebhookPaperReceipt(1, "B", null, "rejected", false,
                                "WEBHOOK_FE_QUESTIONS_REQUIRED", "Đề FE phải có ít nhất một câu hỏi."))));

        assertEquals(HttpStatus.OK,
                controller.receivePaper("eos-crawler", "sig", "{}").getStatusCode());
    }

    private static WebhookPaperReceipt queued(int index) {
        return new WebhookPaperReceipt(index, "A", UUID.randomUUID(), "queued", false, null, null);
    }

    private static WebhookPaperReceipt duplicate(int index) {
        return new WebhookPaperReceipt(index, "A", UUID.randomUUID(), "queued", true, null, null);
    }
}
