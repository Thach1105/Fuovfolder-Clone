package com.fuoverflow.moderation.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.moderation.api.dto.CreateFlagRequest;
import com.fuoverflow.moderation.api.dto.FlagResponse;
import com.fuoverflow.moderation.application.ModerationService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/flags")
public class FlagController {
    private final ModerationService moderationService;

    public FlagController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @PostMapping
    @RequirePermission("forum.flag:create")
    public ApiResponse<FlagResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateFlagRequest request) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(moderationService.createFlag(userId, request));
    }
}
