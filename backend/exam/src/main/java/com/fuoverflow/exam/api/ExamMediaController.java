package com.fuoverflow.exam.api;

import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.common.exception.TooManyRequestsException;
import com.fuoverflow.common.storage.ObjectStorage;
import com.fuoverflow.common.web.ClientIpResolver;
import com.fuoverflow.exam.application.ExamAccessGuard;
import com.fuoverflow.exam.application.ExamMediaTokenService;
import com.fuoverflow.exam.persistence.ExamFeQuestionRepository;
import com.fuoverflow.exam.persistence.ExamPeItemRepository;
import com.fuoverflow.exam.persistence.ExamPeResourceEntity;
import com.fuoverflow.exam.persistence.ExamPeResourceRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/exam")
public class ExamMediaController {
    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    private static final int RATE_LIMIT_MAP_CLEANUP_THRESHOLD = 10_000;
    private static final long RATE_LIMIT_ENTRY_TTL_MS = 120_000;

    private final ExamMediaTokenService tokenService;
    private final ObjectStorage objectStorage;
    private final ExamAccessGuard accessGuard;
    private final ExamFeQuestionRepository feQuestionRepository;
    private final ExamPeItemRepository peItemRepository;
    private final ExamPeResourceRepository peResourceRepository;
    private final ConcurrentHashMap<String, long[]> rateLimitMap = new ConcurrentHashMap<>();

    public ExamMediaController(
            ExamMediaTokenService tokenService,
            ObjectStorage objectStorage,
            ExamAccessGuard accessGuard,
            ExamFeQuestionRepository feQuestionRepository,
            ExamPeItemRepository peItemRepository,
            ExamPeResourceRepository peResourceRepository) {
        this.tokenService = tokenService;
        this.objectStorage = objectStorage;
        this.accessGuard = accessGuard;
        this.feQuestionRepository = feQuestionRepository;
        this.peItemRepository = peItemRepository;
        this.peResourceRepository = peResourceRepository;
    }

    /**
     * Serve an FE/PE image. Signed (HMAC) URLs verify statelessly; unsigned requests fall
     * back to authenticated membership check. Preview images are still signed, so anonymous
     * signed URLs render — the preview slice is bounded server-side in the query service.
     */
    @GetMapping("/media/{encodedKey}")
    public void serveMedia(
            @PathVariable String encodedKey,
            @RequestParam(required = false) String sig,
            @RequestParam(required = false) Long exp,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        checkRateLimit(request);
        String objectKey = tokenService.decodeKey(encodedKey);
        if (sig != null && exp != null) {
            if (tokenService.isExpired(exp)) {
                throw new ForbiddenException("SIGNED_URL_EXPIRED", "Signed URL has expired");
            }
            if (!tokenService.verify(objectKey, sig, exp)) {
                throw new ForbiddenException("INVALID_SIGNATURE", "Invalid media signature");
            }
            streamInline(objectKey, response);
        } else {
            UUID userId = resolveAuthenticatedUserId();
            requireImageIsExamOwned(objectKey);
            accessGuard.requireActiveMembership(userId);
            streamInline(objectKey, response);
        }
    }

    /** Download a PE resource (zip/file). Members only; served as an attachment. */
    @GetMapping("/pe/resources/{resourceId}/download")
    public void downloadResource(
            @PathVariable UUID resourceId,
            HttpServletResponse response) throws Exception {
        UUID userId = resolveAuthenticatedUserId();
        accessGuard.requireActiveMembership(userId);
        ExamPeResourceEntity resource = peResourceRepository.findByIdAndDeletedAtIsNull(resourceId)
                .orElseThrow(() -> new NotFoundException("EXAM_PE_RESOURCE_NOT_FOUND", "Resource not found"));

        String contentType = resource.getMimeType() != null ? resource.getMimeType() : "application/octet-stream";
        String filename = resource.getOriginalFilename() != null ? resource.getOriginalFilename() : "download";
        String encodedName = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(contentType);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename.replace("\"", "") + "\"; filename*=UTF-8''" + encodedName);
        if (resource.getSizeBytes() > 0) {
            response.setContentLengthLong(resource.getSizeBytes());
        }
        try (InputStream input = objectStorage.openStream(resource.getObjectKey());
             OutputStream output = response.getOutputStream()) {
            input.transferTo(output);
        }
    }

    private void streamInline(String objectKey, HttpServletResponse response) throws Exception {
        response.setContentType(detectContentType(objectKey));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, max-age=900");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline");
        try (InputStream input = objectStorage.openStream(objectKey);
             OutputStream output = response.getOutputStream()) {
            input.transferTo(output);
        }
    }

    private void requireImageIsExamOwned(String objectKey) {
        boolean owned = feQuestionRepository.findSubjectIdByQuestionImageUrlsContaining(objectKey).isPresent()
                || peItemRepository.findSubjectIdByExamImageUrlsContaining(objectKey).isPresent();
        if (!owned) {
            throw new NotFoundException("MEDIA_NOT_FOUND", "Media not found");
        }
    }

    private UUID resolveAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            throw new ForbiddenException("UNAUTHORIZED", "Authentication required");
        }
        return UUID.fromString(auth.getName());
    }

    private void checkRateLimit(HttpServletRequest request) {
        String ip = ClientIpResolver.resolve(request);
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000;
        long[] timestamps = rateLimitMap.compute(ip, (k, existing) -> {
            if (existing == null) return new long[]{now, 1};
            if (existing[0] < windowStart) return new long[]{now, 1};
            existing[1]++;
            return existing;
        });
        if (rateLimitMap.size() > RATE_LIMIT_MAP_CLEANUP_THRESHOLD) {
            evictStaleRateLimitEntries(now);
        }
        if (timestamps[1] > MAX_REQUESTS_PER_MINUTE) {
            throw new TooManyRequestsException("RATE_LIMIT_EXCEEDED", "Too many requests. Try again later.");
        }
    }

    private void evictStaleRateLimitEntries(long now) {
        long staleBefore = now - RATE_LIMIT_ENTRY_TTL_MS;
        rateLimitMap.entrySet().removeIf(entry -> entry.getValue()[0] < staleBefore);
    }

    private static String detectContentType(String objectKey) {
        String lower = objectKey.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }
}
