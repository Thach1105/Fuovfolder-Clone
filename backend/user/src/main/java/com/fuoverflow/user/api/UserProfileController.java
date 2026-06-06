package com.fuoverflow.user.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.user.api.dto.UpdateUserProfileRequest;
import com.fuoverflow.user.api.dto.UserProfileResponse;
import com.fuoverflow.user.application.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserProfileController {
    private final UserProfileService service;

    public UserProfileController(UserProfileService service) {
        this.service = service;
    }

    @GetMapping("/me")
    @RequirePermission("user.profile:read")
    public ApiResponse<UserProfileResponse> me(Authentication authentication) {
        return ApiResponse.ok(service.getCurrentUser(UUID.fromString(authentication.getName())));
    }

    @PatchMapping("/me/profile")
    @RequirePermission("user.profile:update")
    public ApiResponse<UserProfileResponse> updateProfile(Authentication authentication, @Valid @RequestBody UpdateUserProfileRequest request) {
        return ApiResponse.ok(service.updateProfile(UUID.fromString(authentication.getName()), request));
    }
}
