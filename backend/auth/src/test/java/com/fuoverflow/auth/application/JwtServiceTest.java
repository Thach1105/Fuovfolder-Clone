package com.fuoverflow.auth.application;

import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.domain.TokenPair;
import com.fuoverflow.auth.support.TokenGenerator;
import com.fuoverflow.auth.support.TokenHashing;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        AuthProperties props = new AuthProperties(
                "fuoverflow", "fuoverflow-api", Duration.ofMinutes(10), Duration.ofDays(30),
                2, "test-pepper",
                new AuthProperties.Cookie(false, "Lax", "fuoverflow_at", "fuoverflow_rt"),
                new AuthProperties.Jwt("test-key", "classpath:keys/test-private.pem", "classpath:keys/test-public.pem"),
                new AuthProperties.EmailVerification(false, null, null, null),
                new AuthProperties.PasswordReset(false, null, null, null, Duration.ofHours(1)));
        jwtService = new JwtService(props, new TokenGenerator(), new TokenHashing(props), new DefaultResourceLoader());
    }

    @Test
    void generateAndDecodeRoundTrip() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        AuthUserView user = new AuthUserView(
                userId, "test@example.com", "testuser", "hash", "Test User",
                UserStatus.ACTIVE, List.of("USER"), 1L, List.of(), false, true, now, now, null);
        UUID sessionId = UUID.randomUUID();

        TokenPair pair = jwtService.generate(user, sessionId, now);

        assertThat(pair.accessToken()).isNotBlank();
        assertThat(pair.refreshToken()).isNotBlank();

        Jwt decoded = jwtService.decode(pair.accessToken());
        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getClaimAsString("sid")).isEqualTo(sessionId.toString());
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(decoded.getClaimAsString("typ")).isEqualTo("access");
    }

    @Test
    void decodedTokenHasCorrectIssuerAndAudience() {
        Instant now = Instant.now();
        AuthUserView user = new AuthUserView(
                UUID.randomUUID(), "a@b.com", "u", "h", "U",
                UserStatus.ACTIVE, List.of("USER"), 1L, List.of(), false, true, now, now, null);
        TokenPair pair = jwtService.generate(user, UUID.randomUUID(), now);
        Jwt decoded = jwtService.decode(pair.accessToken());

        assertThat(decoded.getClaim("iss").toString()).isEqualTo("fuoverflow");
        assertThat(decoded.getAudience()).containsExactly("fuoverflow-api");
    }
}
