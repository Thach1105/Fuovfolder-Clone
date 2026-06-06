package com.fuoverflow.moderation.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.moderation.api.dto.FlagPageResponse;
import com.fuoverflow.moderation.api.dto.FlagResponse;
import com.fuoverflow.moderation.api.dto.ResolveFlagRequest;
import com.fuoverflow.moderation.application.ModerationService;
import com.fuoverflow.post.api.dto.PostPageResponse;
import com.fuoverflow.post.application.PostQueryService;
import com.fuoverflow.post.application.PostWriteService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/moderation")
public class ModerationAdminController {
    private final ModerationService moderationService;
    private final PostQueryService postQueryService;
    private final PostWriteService postWriteService;

    public ModerationAdminController(
            ModerationService moderationService,
            PostQueryService postQueryService,
            PostWriteService postWriteService) {
        this.moderationService = moderationService;
        this.postQueryService = postQueryService;
        this.postWriteService = postWriteService;
    }

    @GetMapping("/flags")
    @RequirePermission("forum.moderation:read")
    public ApiResponse<FlagPageResponse> listFlags(
            @RequestParam(defaultValue = "open") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(moderationService.listFlags(status, page, size));
    }

    @PostMapping("/flags/{flagId}/resolve")
    @RequirePermission("forum.moderation:update")
    public ApiResponse<FlagResponse> resolveFlag(
            @PathVariable UUID flagId,
            Authentication authentication,
            @Valid @RequestBody ResolveFlagRequest request) {
        UUID actorUserId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(moderationService.resolveFlag(flagId, actorUserId, request));
    }

    @GetMapping("/queue")
    @RequirePermission("forum.moderation:read")
    public ApiResponse<PostPageResponse> moderationQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(postQueryService.listPending(page, size));
    }

    @PostMapping("/queue/{postId}/approve")
    @RequirePermission("forum.moderation:update")
    public ApiResponse<Void> approvePost(
            @PathVariable UUID postId,
            Authentication authentication) {
        UUID actorUserId = UUID.fromString(authentication.getName());
        postWriteService.approvePost(postId, actorUserId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/queue/{postId}/reject")
    @RequirePermission("forum.moderation:update")
    public ApiResponse<Void> rejectPost(
            @PathVariable UUID postId,
            Authentication authentication) {
        UUID actorUserId = UUID.fromString(authentication.getName());
        postWriteService.rejectPost(postId, actorUserId);
        return ApiResponse.ok(null);
    }
}
