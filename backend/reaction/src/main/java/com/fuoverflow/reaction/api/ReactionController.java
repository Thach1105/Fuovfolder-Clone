package com.fuoverflow.reaction.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.reaction.api.dto.ReactionRequest;
import com.fuoverflow.reaction.api.dto.ReactionStatusResponse;
import com.fuoverflow.reaction.application.ReactionService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts/{postId}/reactions")
public class ReactionController {
    private final ReactionService reactionService;

    public ReactionController(ReactionService reactionService) {
        this.reactionService = reactionService;
    }

    @GetMapping
    @RequirePermission(value = "forum.post:read", allowAnonymous = true)
    public ApiResponse<ReactionStatusResponse> status(
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "like") String type) {
        return ApiResponse.ok(reactionService.status(postId, resolveUserId().orElse(null), type));
    }

    @PostMapping
    @RequirePermission("forum.reaction:create")
    public ApiResponse<ReactionStatusResponse> add(
            @PathVariable UUID postId,
            Authentication authentication,
            @RequestBody(required = false) ReactionRequest request) {
        UUID userId = UUID.fromString(authentication.getName());
        String type = request == null || request.type() == null ? "like" : request.type();
        return ApiResponse.ok(reactionService.add(postId, userId, type));
    }

    @DeleteMapping("/{type}")
    @RequirePermission("forum.reaction:delete")
    public ApiResponse<ReactionStatusResponse> remove(
            @PathVariable UUID postId,
            @PathVariable String type,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(reactionService.remove(postId, userId, type));
    }

    private Optional<UUID> resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(authentication.getName()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
