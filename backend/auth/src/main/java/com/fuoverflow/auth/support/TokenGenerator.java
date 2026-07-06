package com.fuoverflow.auth.support;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class TokenGenerator {
    private final SecureRandom secureRandom = new SecureRandom();

    public String opaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String verificationCode(int length) {
        int bound = (int) Math.pow(10, length);
        int code = secureRandom.nextInt(bound);
        return String.format("%0" + length + "d", code);
    }
}
