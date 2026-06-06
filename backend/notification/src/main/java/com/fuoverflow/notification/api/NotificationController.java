package com.fuoverflow.notification.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.notification.api.dto.NotificationPageResponse;
import com.fuoverflow.notification.api.dto.NotificationResponse;
import com.fuoverflow.notification.api.dto.UnreadCountResponse;
import com.fuoverflow.notification.application.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @RequirePermission("notification:read")
    public ApiResponse<NotificationPageResponse> list(
            Authentication authentication,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(notificationService.list(userId, unreadOnly, page, size));
    }

    @GetMapping("/unread-count")
    @RequirePermission("notification:read")
    public ApiResponse<UnreadCountResponse> unreadCount(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(notificationService.unreadCount(userId));
    }

    @PatchMapping("/{notificationId}/read")
    @RequirePermission("notification:update")
    public ApiResponse<NotificationResponse> markRead(
            Authentication authentication,
            @PathVariable UUID notificationId) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(notificationService.markRead(userId, notificationId));
    }

    @PostMapping("/mark-all-read")
    @RequirePermission("notification:update")
    public ApiResponse<Map<String, Integer>> markAllRead(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        int updated = notificationService.markAllRead(userId);
        return ApiResponse.ok(Map.of("updated", updated));
    }
}
