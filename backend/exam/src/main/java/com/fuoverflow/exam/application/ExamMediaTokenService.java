package com.fuoverflow.exam.application;

import com.fuoverflow.exam.config.ExamProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class ExamMediaTokenService {
    private static final String HMAC_ALGO = "HmacSHA256";

    private final ExamProperties properties;

    public ExamMediaTokenService(ExamProperties properties) {
        this.properties = properties;
    }

    public String generateSignedUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        long exp = Instant.now().plusSeconds(properties.signedUrlTtlSecondsOrDefault()).getEpochSecond();
        String sig = sign(objectKey, exp);
        String encodedKey = encodeKey(objectKey);
        return "/api/v1/exam/media/" + encodedKey + "?sig=" + sig + "&exp=" + exp;
    }

    public boolean verify(String objectKey, String signature, long expiry) {
        if (Instant.now().getEpochSecond() > expiry) {
            return false;
        }
        String expected = sign(objectKey, expiry);
        return constantTimeEquals(expected, signature);
    }

    public boolean isExpired(long expiry) {
        return Instant.now().getEpochSecond() > expiry;
    }

    public String encodeKey(String objectKey) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(objectKey.getBytes(StandardCharsets.UTF_8));
    }

    public String decodeKey(String encodedKey) {
        return new String(Base64.getUrlDecoder().decode(encodedKey), StandardCharsets.UTF_8);
    }

    private String sign(String objectKey, long expiry) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(
                    properties.mediaSigningSecretOrDefault().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGO));
            String payload = objectKey + ":" + expiry;
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign media token", e);
        }
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
}
