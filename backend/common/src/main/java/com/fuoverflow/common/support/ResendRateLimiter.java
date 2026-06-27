package com.fuoverflow.common.support;

import com.fuoverflow.common.exception.TooManyRequestsException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class ResendRateLimiter {

    private static final int COOLDOWN_SECONDS = 120;
    private static final int DAILY_MAX = 20;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final StringRedisTemplate redis;

    public ResendRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void checkAndRecord(String type, UUID userId) {
        String cooldownKey = cooldownKey(type, userId);
        String dailyKey = dailyKey(type, userId);

        if (Boolean.TRUE.equals(redis.hasKey(cooldownKey))) {
            Long ttl = redis.getExpire(cooldownKey, TimeUnit.SECONDS);
            long remaining = ttl != null && ttl > 0 ? ttl : COOLDOWN_SECONDS;
            throw new TooManyRequestsException("RESEND_TOO_SOON",
                    String.format("Vui lòng chờ %d giây trước khi gửi lại.", remaining));
        }

        String dailyCountStr = redis.opsForValue().get(dailyKey);
        int dailyCount = dailyCountStr != null ? Integer.parseInt(dailyCountStr) : 0;
        if (dailyCount >= DAILY_MAX) {
            throw new TooManyRequestsException("RESEND_DAILY_LIMIT_EXCEEDED",
                    "Bạn đã đạt giới hạn gửi email trong ngày hôm nay. Vui lòng thử lại vào ngày mai.");
        }

        redis.opsForValue().set(cooldownKey, "1", COOLDOWN_SECONDS, TimeUnit.SECONDS);
        Long newCount = redis.opsForValue().increment(dailyKey);
        if (newCount != null && newCount == 1) {
            redis.expire(dailyKey, 24, TimeUnit.HOURS);
        }
    }

    private String cooldownKey(String type, UUID userId) {
        return String.format("resend:cooldown:%s:%s", type, userId);
    }

    private String dailyKey(String type, UUID userId) {
        String date = LocalDate.now(ZoneOffset.UTC).format(DATE_FORMAT);
        return String.format("resend:daily:%s:%s:%s", type, userId, date);
    }
}
