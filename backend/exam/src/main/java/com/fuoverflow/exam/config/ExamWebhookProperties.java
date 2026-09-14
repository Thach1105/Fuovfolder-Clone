package com.fuoverflow.exam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Webhook ingest settings. Every limit has a default so a deployment that configures nothing still
 * refuses an unbounded payload; {@code clients} is the only value that must be supplied, since an
 * empty map means no sender can authenticate.
 */
@ConfigurationProperties(prefix = "fuexam.exam.webhook")
public record ExamWebhookProperties(
        Map<String, String> clients,
        List<String> allowedResourceHosts,
        Long maxPayloadBytes,
        Long maxImageBytes,
        Long maxResourceBytes,
        Integer maxQuestions,
        Integer maxAttempts,
        Integer signatureToleranceSeconds,
        Long pollIntervalMs,
        Integer maxPapersPerBatch
) {
    public Map<String, String> clientsOrEmpty() {
        return clients == null ? Map.of() : clients;
    }

    public List<String> allowedResourceHostsOrEmpty() {
        return allowedResourceHosts == null ? List.of() : allowedResourceHosts;
    }

    public long maxPayloadBytesOrDefault() {
        return maxPayloadBytes != null && maxPayloadBytes > 0 ? maxPayloadBytes : 33_554_432L;
    }

    public long maxImageBytesOrDefault() {
        return maxImageBytes != null && maxImageBytes > 0 ? maxImageBytes : 5_242_880L;
    }

    public long maxResourceBytesOrDefault() {
        return maxResourceBytes != null && maxResourceBytes > 0 ? maxResourceBytes : 52_428_800L;
    }

    public int maxQuestionsOrDefault() {
        return maxQuestions != null && maxQuestions > 0 ? maxQuestions : 200;
    }

    public int maxAttemptsOrDefault() {
        return maxAttempts != null && maxAttempts > 0 ? maxAttempts : 5;
    }

    public int signatureToleranceSecondsOrDefault() {
        return signatureToleranceSeconds != null && signatureToleranceSeconds > 0
                ? signatureToleranceSeconds : 300;
    }

    public int maxPapersPerBatchOrDefault() {
        return maxPapersPerBatch != null && maxPapersPerBatch > 0 ? maxPapersPerBatch : 50;
    }
}
