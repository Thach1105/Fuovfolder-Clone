package com.fuoverflow.auth.api;

import com.fuoverflow.auth.api.dto.ChangePasswordRequest;
import com.fuoverflow.auth.application.JwtService;
import com.fuoverflow.auth.application.UserPasswordManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserPasswordController {
    private final UserPasswordManagementService service;
    private final JwtService jwtService;

    public UserPasswordController(UserPasswordManagementService service, JwtService jwtService) {
        this.service = service;
        this.jwtService = jwtService;
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        UUID userId = UUID.fromString(authentication.getName());
        UUID sessionId = extractSessionId(authentication);
        service.changePassword(userId, request.currentPassword(), request.newPassword(), sessionId);
    }

    private UUID extractSessionId(Authentication authentication) {
        String token = (String) authentication.getCredentials();
        Jwt jwt = jwtService.decode(token);
        String sid = jwt.getClaimAsString("sid");
        if (sid == null) {
            throw new IllegalStateException("Session ID not found in JWT");
        }
        return UUID.fromString(sid);
    }
}
