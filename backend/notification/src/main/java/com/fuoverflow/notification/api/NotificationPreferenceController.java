package com.fuoverflow.notification.api;

import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.notification.api.dto.NotificationPreferenceUpdateRequest;
import com.fuoverflow.notification.api.dto.NotificationPreferencesResponse;
import com.fuoverflow.notification.application.NotificationPreferenceService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/notification-preferences")
public class NotificationPreferenceController {
    private final NotificationPreferenceService preferenceService;

    public NotificationPreferenceController(NotificationPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ApiResponse<NotificationPreferencesResponse> get(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(preferenceService.getForUser(userId));
    }

    @PutMapping
    public ApiResponse<NotificationPreferencesResponse> update(
            Authentication authentication,
            @Valid @RequestBody NotificationPreferenceUpdateRequest request) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(preferenceService.update(userId, request));
    }
}
