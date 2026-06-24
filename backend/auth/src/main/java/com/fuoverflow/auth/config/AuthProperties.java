package com.fuoverflow.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        String issuer,
        String audience,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String refreshTokenHashPepper,
        Cookie cookie,
        Jwt jwt,
        EmailVerification emailVerification,
        PasswordReset passwordReset
) {
    public record Cookie(boolean secure, String sameSite, String accessName, String refreshName) {}
    public record Jwt(String keyId, String privateKeyLocation, String publicKeyLocation) {}
    public record EmailVerification(boolean enabled, String from, String verificationUrlBase, String subject) {}
    public record PasswordReset(boolean enabled, String from, String resetUrlBase, String subject, Duration tokenTtl) {}
}
