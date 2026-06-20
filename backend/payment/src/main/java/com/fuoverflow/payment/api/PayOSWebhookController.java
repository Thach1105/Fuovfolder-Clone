package com.fuoverflow.payment.api;

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

import java.time.Instant;
import java.util.Optional;

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
        log.info("Received PayOS webhook");
        try {
            WebhookData webhookData = payOS.webhooks().verify(body);

            String orderCode = String.valueOf(webhookData.getOrderCode());
            String providerEventId = webhookData.getReference() != null
                    ? webhookData.getReference() : orderCode;
            boolean paid = "00".equals(webhookData.getCode());

            Optional<PaymentWebhookEventEntity> existing =
                    webhookRepo.findByProviderAndProviderEventId("payos", providerEventId);
            if (existing.isPresent() && existing.get().getProcessedAt() != null) {
                log.info("Webhook already processed for orderCode={}", orderCode);
                return ResponseEntity.ok("OK");
            }

            PaymentWebhookEventEntity event = PaymentWebhookEventEntity.create(
                    "payos", providerEventId, "payment.success", body, true);
            webhookRepo.save(event);

            if (paid) {
                paymentService.confirmPaymentByOrderCode(orderCode);
            }

            event.markProcessed(Instant.now());
            webhookRepo.save(event);

            return ResponseEntity.ok("OK");
        } catch (PayOSException e) {
            log.warn("Invalid PayOS webhook signature: {}", e.getMessage());
            PaymentWebhookEventEntity event = PaymentWebhookEventEntity.create(
                    "payos", "unknown", "payment.invalid", body, false);
            webhookRepo.save(event);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            log.error("Failed to process PayOS webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error");
        }
    }
}
