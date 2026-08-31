package com.fuoverflow.exam.application;

import com.fuoverflow.common.exception.UnauthorizedException;
import com.fuoverflow.exam.config.ExamWebhookProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Authenticates a webhook delivery by HMAC over the exact request body.
 *
 * <p>The signed message includes the timestamp, so a captured body cannot be replayed once the
 * tolerance window closes, and the comparison is constant-time so a wrong signature leaks nothing
 * about how much of it was right. Nothing here is ever logged: the header and the secret are both
 * credentials.
 */
@Component
public class ExamWebhookSignatureVerifier {
    private static final String HMAC_ALGO = "HmacSHA256";

    private final ExamWebhookProperties properties;

    public ExamWebhookSignatureVerifier(ExamWebhookProperties properties) {
        this.properties = properties;
    }

    public void verify(String clientId, String rawBody, String signatureHeader) {
        String secret = clientId == null ? null : properties.clientsOrEmpty().get(clientId.trim());
        if (secret == null || secret.isBlank()) {
            throw new UnauthorizedException("WEBHOOK_CLIENT_UNKNOWN",
                    "Client không được cấp quyền gọi webhook.");
        }

        ParsedHeader parsed = parse(signatureHeader);
        long skew = Math.abs(Instant.now().getEpochSecond() - parsed.timestamp());
        if (skew > properties.signatureToleranceSecondsOrDefault()) {
            throw new UnauthorizedException("WEBHOOK_SIGNATURE_EXPIRED",
                    "Chữ ký đã hết hiệu lực.");
        }

        String expected = sign(secret, parsed.timestamp(), rawBody);
        if (!constantTimeEquals(expected, parsed.signature())) {
            throw new UnauthorizedException("WEBHOOK_SIGNATURE_INVALID", "Chữ ký không hợp lệ.");
        }
    }

    public static String sign(String secret, long timestamp, String rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            String payload = timestamp + "." + (rawBody == null ? "" : rawBody);
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign webhook payload", e);
        }
    }

    private static ParsedHeader parse(String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw malformed();
        }
        Long timestamp = null;
        String signature = null;
        for (String part : signatureHeader.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            String key = pair[0].trim();
            String value = pair[1].trim();
            if ("t".equals(key)) {
                try {
                    timestamp = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw malformed();
                }
            } else if ("v1".equals(key)) {
                signature = value;
            }
        }
        if (timestamp == null || signature == null || signature.isEmpty()) {
            throw malformed();
        }
        return new ParsedHeader(timestamp, signature);
    }

    private static UnauthorizedException malformed() {
        return new UnauthorizedException("WEBHOOK_SIGNATURE_MALFORMED",
                "Header chữ ký không đúng dạng t=<unix>,v1=<hex>.");
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private record ParsedHeader(long timestamp, String signature) {
    }
}
