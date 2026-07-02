package com.fuoverflow.payment.api;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.payment.application.PaymentService;
import com.fuoverflow.payment.persistence.PaymentWebhookEventEntity;
import com.fuoverflow.payment.persistence.PaymentWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.payos.PayOS;
import vn.payos.exception.PayOSException;
import vn.payos.model.webhooks.WebhookData;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payment/payos")
public class PayOSWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PayOSWebhookController.class);

    private final PaymentService paymentService;
    private final PaymentWebhookEventRepository webhookRepo;
    private final PayOS payOS;

    public PayOSWebhookController(PaymentService paymentService,
                                  PaymentWebhookEventRepository webhookRepo,
                                  PayOS payOS) {
        this.paymentService = paymentService;
        this.webhookRepo = webhookRepo;
        this.payOS = payOS;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody String body) {
        log.info("Received PayOS webhook: bodyLength={}",
                body != null ? body.length() : 0);
        try {
            WebhookData webhookData = payOS.webhooks().verify(body);
            log.info("PayOS webhook verified: orderCode={} code={}",
                    webhookData.getOrderCode(), webhookData.getCode());

            String orderCode = String.valueOf(webhookData.getOrderCode());
            String providerEventId = deriveProviderEventId(webhookData);
            boolean paid = "00".equals(webhookData.getCode());

            log.info("PayOS webhook processing: orderCode={} providerEventId={} paid={}",
                    orderCode, providerEventId, paid);

            Optional<PaymentWebhookEventEntity> existing =
                    webhookRepo.findByProviderAndProviderEventId("payos", providerEventId);
            if (existing.isPresent()) {
                log.info("PayOS webhook duplicate: orderCode={} providerEventId={} alreadyProcessed={}",
                        orderCode, providerEventId, existing.get().getProcessedAt() != null);
                if (existing.get().getProcessedAt() != null) {
                    log.info("Webhook already processed for orderCode={}", orderCode);
                    return ResponseEntity.ok("OK");
                }
                if (paid) {
                    confirmOrLogUnknownOrder(orderCode);
                }
                existing.get().markProcessed(Instant.now());
                webhookRepo.save(existing.get());
                return ResponseEntity.ok("OK");
            }

            PaymentWebhookEventEntity event = PaymentWebhookEventEntity.create(
                    "payos", providerEventId, paid ? "payment.success" : "payment.failed", body, true);
            webhookRepo.save(event);
            log.info("PayOS webhook event saved: id={} providerEventId={}", event.getId(), providerEventId);

            if (paid) {
                confirmOrLogUnknownOrder(orderCode);
            }

            event.markProcessed(Instant.now());
            webhookRepo.save(event);

            return ResponseEntity.ok("OK");
        } catch (PayOSException e) {
            log.warn("Invalid PayOS webhook signature: {}", e.getMessage());
            String invalidEventId = invalidEventId(body);
            if (webhookRepo.findByProviderAndProviderEventId("payos", invalidEventId).isEmpty()
                    && isJsonPayload(body)) {
                PaymentWebhookEventEntity event = PaymentWebhookEventEntity.create(
                        "payos", invalidEventId, "payment.invalid", body, false);
                webhookRepo.save(event);
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            log.error("Failed to process PayOS webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error");
        }
    }

    static boolean isJsonPayload(String body) {
        if (body == null) {
            return false;
        }
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        char first = trimmed.charAt(0);
        char last = trimmed.charAt(trimmed.length() - 1);
        return (first == '{' && last == '}') || (first == '[' && last == ']');
    }

    /**
     * PayOS sends a sample payload (orderCode=123, amount=3000, description=VQRIO123, ...)
     * as a connectivity check when the merchant saves the webhook URL.
     * We must acknowledge with 2XX but obviously have no matching order in our DB.
     * Treat the unknown order as a no-op and keep the event row for audit.
     */
    private void confirmOrLogUnknownOrder(String orderCode) {
        try {
            paymentService.confirmPaymentByOrderCode(orderCode);
            log.info("PayOS payment confirmed for orderCode={}", orderCode);
        } catch (NotFoundException e) {
            log.warn("PayOS webhook for unknown orderCode={} (likely PayOS connectivity test or stale event). " +
                    "Acknowledging with 200; webhook event row kept for audit.", orderCode);
        }
    }

    static String deriveProviderEventId(WebhookData webhookData) {
        if (webhookData.getReference() != null && !webhookData.getReference().isBlank()) {
            return webhookData.getReference();
        }
        return "payos-order-" + webhookData.getOrderCode() + "-code-" + webhookData.getCode();
    }

    static String invalidEventId(String body) {
        return "invalid-" + UUID.nameUUIDFromBytes(body.getBytes(StandardCharsets.UTF_8));
    }
}
