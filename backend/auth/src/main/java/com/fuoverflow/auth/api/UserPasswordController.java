package com.fuoverflow.auth.api;

import com.fuoverflow.auth.api.dto.ChangePasswordRequest;
import com.fuoverflow.auth.application.ChangePasswordService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserPasswordController {
    private final ChangePasswordService changePasswordService;

    public UserPasswordController(ChangePasswordService changePasswordService) {
        this.changePasswordService = changePasswordService;
    }

    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request,
                               Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        UUID sessionId = UUID.fromString(
                ((JwtAuthenticationToken) authentication).getToken().getClaimAsString("sid"));
        changePasswordService.changePassword(userId, sessionId, request.currentPassword(), request.newPassword());
    }
}
