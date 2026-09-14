package com.fuoverflow.exam.api;

import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.exam.api.dto.webhook.WebhookBatchReceiptResponse;
import com.fuoverflow.exam.application.ExamWebhookReceiptService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingest endpoint for third-party exam paper deliveries. Authenticated by the HMAC signature in
 * {@code X-Exam-Signature}, not by a session, so it is {@code permitAll} in the security config.
 */
@RestController
@RequestMapping("/api/v1/exam/webhook")
public class ExamWebhookController {
    private final ExamWebhookReceiptService receiptService;

    public ExamWebhookController(ExamWebhookReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    /**
     * The body is taken as a raw string because the signature covers those exact bytes: letting
     * Spring bind and re-serialise it would change the message being verified.
     */
    @PostMapping("/papers")
    public ResponseEntity<ApiResponse<WebhookBatchReceiptResponse>> receivePaper(
            @RequestHeader(value = "X-Exam-Client", required = false) String clientId,
            @RequestHeader(value = "X-Exam-Signature", required = false) String signature,
            @RequestBody(required = false) String rawBody) {
        WebhookBatchReceiptResponse receipt = receiptService.receive(clientId, rawBody, signature);
        HttpStatus status = receipt.accepted() > 0 ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(receipt));
    }
}
