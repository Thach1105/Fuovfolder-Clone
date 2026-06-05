package com.fuoverflow.notification.api;

import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.notification.api.dto.PushSubscribeRequest;
import com.fuoverflow.notification.api.dto.VapidPublicKeyResponse;
import com.fuoverflow.notification.application.PushSubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PushNotificationController {
    private final PushSubscriptionService pushSubscriptionService;

    public PushNotificationController(PushSubscriptionService pushSubscriptionService) {
        this.pushSubscriptionService = pushSubscriptionService;
    }

    @GetMapping("/push/vapid-public-key")
    public ApiResponse<VapidPublicKeyResponse> vapidPublicKey() {
        return ApiResponse.ok(new VapidPublicKeyResponse(
                pushSubscriptionService.publicKey(),
                pushSubscriptionService.pushEnabled()));
    }

    @PostMapping("/users/me/push-subscriptions")
    public ApiResponse<Map<String, Boolean>> subscribe(
            Authentication authentication,
            HttpServletRequest request,
            @Valid @RequestBody PushSubscribeRequest body) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(pushSubscriptionService.subscribe(
                userId,
                body,
                request.getHeader("User-Agent")));
    }

    @DeleteMapping("/users/me/push-subscriptions")
    public ApiResponse<Map<String, Boolean>> unsubscribe(@RequestParam String endpoint) {
        pushSubscriptionService.unsubscribe(endpoint);
        return ApiResponse.ok(Map.of("subscribed", false));
    }
}
