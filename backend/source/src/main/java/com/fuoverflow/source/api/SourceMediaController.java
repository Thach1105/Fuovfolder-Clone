package com.fuoverflow.source.api;

import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.common.storage.ObjectStorage;
import com.fuoverflow.source.application.SourceAccessGuard;
import com.fuoverflow.source.application.SourceMediaTokenService;
import com.fuoverflow.source.persistence.SourceQuestionOptionRepository;
import com.fuoverflow.source.persistence.SourceQuestionRepository;
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
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/source/media")
public class SourceMediaController {
    private final SourceMediaTokenService tokenService;
    private final ObjectStorage objectStorage;
    private final SourceAccessGuard accessGuard;
    private final SourceQuestionRepository questionRepository;
    private final SourceQuestionOptionRepository optionRepository;

    public SourceMediaController(
            SourceMediaTokenService tokenService,
            ObjectStorage objectStorage,
            SourceAccessGuard accessGuard,
            SourceQuestionRepository questionRepository,
            SourceQuestionOptionRepository optionRepository) {
        this.tokenService = tokenService;
        this.objectStorage = objectStorage;
        this.accessGuard = accessGuard;
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
    }

    @GetMapping("/{encodedKey}")
    public void serveMedia(
            @PathVariable String encodedKey,
            @RequestParam(required = false) String sig,
            @RequestParam(required = false) Long exp,
            HttpServletResponse response) throws Exception {

        String objectKey = tokenService.decodeKey(encodedKey);

        if (sig != null && exp != null) {
            serveWithSignature(objectKey, sig, exp, response);
        } else {
            serveWithAuth(objectKey, response);
        }
    }

    private void serveWithSignature(String objectKey, String sig, long exp,
                                     HttpServletResponse response) throws Exception {
        if (tokenService.isExpired(exp)) {
            throw new ForbiddenException("SIGNED_URL_EXPIRED", "Signed URL has expired");
        }
        if (!tokenService.verify(objectKey, sig, exp)) {
            throw new ForbiddenException("INVALID_SIGNATURE", "Invalid media signature");
        }
        streamFile(objectKey, response);
    }

    private void serveWithAuth(String objectKey, HttpServletResponse response) throws Exception {
        UUID userId = resolveAuthenticatedUserId();
        UUID catalogItemId = findCatalogItemId(objectKey);
        accessGuard.requireActiveAccess(userId, catalogItemId);
        streamFile(objectKey, response);
    }

    private void streamFile(String objectKey, HttpServletResponse response) throws Exception {
        String contentType = detectContentType(objectKey);
        response.setContentType(contentType);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, max-age=900");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline");
        try (InputStream input = objectStorage.openStream(objectKey);
             OutputStream output = response.getOutputStream()) {
            input.transferTo(output);
        }
    }

    private UUID findCatalogItemId(String objectKey) {
        Optional<UUID> catalogItemId = questionRepository.findCatalogItemIdByQuestionImageUrl(objectKey);
        if (catalogItemId.isPresent()) {
            return catalogItemId.get();
        }
        catalogItemId = questionRepository.findCatalogItemIdByQuestionImageUrlsContaining(objectKey);
        if (catalogItemId.isPresent()) {
            return catalogItemId.get();
        }
        catalogItemId = optionRepository.findCatalogItemIdByOptionImageUrl(objectKey);
        if (catalogItemId.isPresent()) {
            return catalogItemId.get();
        }
        throw new NotFoundException("MEDIA_NOT_FOUND", "Media not found");
    }

    private UUID resolveAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            throw new ForbiddenException("UNAUTHORIZED", "Authentication required");
        }
        return UUID.fromString(auth.getName());
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
