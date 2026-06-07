package com.fuoverflow.material.application;

import com.fuoverflow.common.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UploadRateLimiter {
    private final Map<UUID, Deque<Instant>> uploadsByUser = new ConcurrentHashMap<>();

    public void checkAllowed(UUID userId, int maxUploadsPerHour) {
        Instant cutoff = Instant.now().minusSeconds(3600);
        Deque<Instant> recent = uploadsByUser.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
                recent.removeFirst();
            }
            if (recent.size() >= maxUploadsPerHour) {
                throw new BadRequestException("UPLOAD_RATE_LIMIT", "Too many uploads. Try again later.");
            }
            recent.addLast(Instant.now());
        }
    }
}
