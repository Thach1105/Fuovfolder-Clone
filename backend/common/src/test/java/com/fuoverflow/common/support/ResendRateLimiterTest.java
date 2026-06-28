package com.fuoverflow.common.support;

import com.fuoverflow.common.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResendRateLimiterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private ResendRateLimiter limiter;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        limiter = new ResendRateLimiter(redis);
    }

    @Test
    void shouldThrowWhenCooldownKeyExists() {
        UUID userId = UUID.randomUUID();
        when(ops.get(contains("daily"))).thenReturn("5");
        when(ops.setIfAbsent(contains("cooldown"), eq("1"), eq(120L), eq(TimeUnit.SECONDS))).thenReturn(false);
        when(redis.getExpire(contains("cooldown"), eq(TimeUnit.SECONDS))).thenReturn(60L);

        assertThatThrownBy(() -> limiter.checkAndRecord("email_verify", userId))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(ex -> {
                    var apiEx = (TooManyRequestsException) ex;
                    assertThat(apiEx.code()).isEqualTo("RESEND_TOO_SOON");
                });
    }

    @Test
    void shouldThrowWhenDailyLimitReached() {
        UUID userId = UUID.randomUUID();
        when(ops.get(contains("daily"))).thenReturn("20");

        assertThatThrownBy(() -> limiter.checkAndRecord("email_verify", userId))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(ex -> {
                    var apiEx = (TooManyRequestsException) ex;
                    assertThat(apiEx.code()).isEqualTo("RESEND_DAILY_LIMIT_EXCEEDED");
                });
    }

    @Test
    void shouldRecordAttemptWhenAllowed() {
        UUID userId = UUID.randomUUID();
        when(ops.get(contains("daily"))).thenReturn("5");
        when(ops.setIfAbsent(contains("cooldown"), eq("1"), eq(120L), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(redis.getExpire(contains("daily"), eq(TimeUnit.SECONDS))).thenReturn(86400L);

        limiter.checkAndRecord("email_verify", userId);

        verify(ops).setIfAbsent(contains("cooldown"), eq("1"), eq(120L), eq(TimeUnit.SECONDS));
        verify(ops).increment(contains("daily"));
    }

    @Test
    void shouldSetDailyKeyTtlWhenNoTtlExists() {
        UUID userId = UUID.randomUUID();
        when(ops.get(contains("daily"))).thenReturn(null);
        when(ops.setIfAbsent(contains("cooldown"), eq("1"), eq(120L), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(redis.getExpire(contains("daily"), eq(TimeUnit.SECONDS))).thenReturn(-1L);

        limiter.checkAndRecord("email_verify", userId);

        verify(redis).expire(contains("daily"), eq(24L), eq(TimeUnit.HOURS));
    }
}
