package com.fuoverflow.common.support;

import com.fuoverflow.common.config.RateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final String BLOCKED_KEY_PREFIX = "ratelimit:blocked:";
    private static final String VIOLATION_KEY_PREFIX = "ratelimit:violations:";

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;
    private final DefaultRedisScript<Long> rateLimitScript;

    public RateLimitService(StringRedisTemplate redis, RateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
        this.rateLimitScript = new DefaultRedisScript<>();
        this.rateLimitScript.setLocation(new ClassPathResource("scripts/rate-limit.lua"));
        this.rateLimitScript.setResultType(Long.class);
    }

    public boolean isAllowed(String key, int maxRequests, Duration window) {
        try {
            long now = Instant.now().toEpochMilli();
            long windowStart = now - window.toMillis();
            long expireSeconds = window.toSeconds() + 1;
            String member = now + ":" + UUID.randomUUID().toString().substring(0, 8);

            Long result = redis.execute(rateLimitScript,
                    List.of(key),
                    String.valueOf(windowStart),
                    String.valueOf(now),
                    String.valueOf(maxRequests),
                    String.valueOf(expireSeconds),
                    member);

            return result != null && result > 0;
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable for rate limiting, failing open: {}", e.getMessage());
            return true;
        }
    }

    public long getRemainingRequests(String key, int maxRequests, Duration window) {
        try {
            long windowStart = Instant.now().toEpochMilli() - window.toMillis();
            redis.opsForZSet().removeRangeByScore(key, 0, windowStart);
            Long count = redis.opsForZSet().zCard(key);
            long current = count != null ? count : 0;
            return Math.max(0, maxRequests - current);
        } catch (RedisConnectionFailureException e) {
            return maxRequests;
        }
    }

    public boolean isBlocked(String ip) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(BLOCKED_KEY_PREFIX + ip));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable for block check, failing open: {}", e.getMessage());
            return false;
        }
    }

    public void blockIp(String ip, Duration duration) {
        try {
            redis.opsForValue().set(BLOCKED_KEY_PREFIX + ip, "blocked", duration);
            log.warn("IP blocked: ip={} duration={}", ip, duration);
        } catch (RedisConnectionFailureException e) {
            log.error("Failed to block IP in Redis: {}", e.getMessage());
        }
    }

    public void recordViolation(String ip) {
        try {
            String key = VIOLATION_KEY_PREFIX + ip;
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, 1, TimeUnit.HOURS);
            }
            if (count != null && count >= properties.block().violationsBeforeBlock()) {
                blockIp(ip, properties.block().autoBlockDuration());
                redis.delete(key);
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable for violation recording: {}", e.getMessage());
        }
    }
}
