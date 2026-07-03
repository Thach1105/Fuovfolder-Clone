package com.fuoverflow.material.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.material.api.dto.UploadResponse;
import com.fuoverflow.material.application.MediaAccessService;
import com.fuoverflow.material.application.UploadService;
import com.fuoverflow.material.domain.UploadPurpose;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {
    private final UploadService uploadService;
    private final MediaAccessService mediaAccessService;

    public MediaController(UploadService uploadService, MediaAccessService mediaAccessService) {
        this.uploadService = uploadService;
        this.mediaAccessService = mediaAccessService;
    }

    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("media.upload:create")
    public ApiResponse<UploadResponse> upload(
            Authentication authentication,
            @RequestPart("file") MultipartFile file,
            @RequestParam("purpose") String purpose) {
        UUID userId = UUID.fromString(authentication.getName());
        UploadPurpose uploadPurpose = UploadPurpose.fromSlug(purpose.trim());
        return ApiResponse.ok(uploadService.upload(file, uploadPurpose, userId, authentication));
    }

    @GetMapping("/files/{fileId}")
    @RequirePermission(value = "media.file:read", allowAnonymous = true)
    public void download(@PathVariable UUID fileId, HttpServletResponse response) throws Exception {
        MediaAccessService.DownloadResource resource = mediaAccessService.openDownload(fileId, resolveViewerUserId());
        String encoded = URLEncoder.encode(resource.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(resource.mimeType());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
        if (resource.sizeBytes() > 0) {
            response.setContentLengthLong(resource.sizeBytes());
        }
        try (InputStream input = resource.stream(); OutputStream output = response.getOutputStream()) {
            input.transferTo(output);
        }
    }

    private UUID resolveViewerUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
