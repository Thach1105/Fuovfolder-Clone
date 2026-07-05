package com.fuoverflow.auth.api;

import com.fuoverflow.auth.api.dto.SessionListResponse;
import com.fuoverflow.auth.api.dto.SetDeviceLimitRequest;
import com.fuoverflow.auth.application.SessionManagementService;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.user.persistence.UserEntity;
import com.fuoverflow.user.persistence.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users/{userId}")
@RequirePermission("admin.panel:access")
public class AdminSessionController {
    private final SessionManagementService sessionManagement;
    private final UserRepository userRepository;

    public AdminSessionController(SessionManagementService sessionManagement,
                                   UserRepository userRepository) {
        this.sessionManagement = sessionManagement;
        this.userRepository = userRepository;
    }

    @GetMapping("/sessions")
    @RequirePermission("admin.user:read")
    public ApiResponse<SessionListResponse> listSessions(@PathVariable UUID userId) {
        return ApiResponse.ok(sessionManagement.listSessions(userId, null));
    }

    @DeleteMapping("/sessions/{familyId}")
    @RequirePermission("admin.user:update")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@PathVariable UUID userId, @PathVariable UUID familyId) {
        sessionManagement.adminRevokeSession(familyId);
    }

    @DeleteMapping("/sessions")
    @RequirePermission("admin.user:update")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAllSessions(@PathVariable UUID userId) {
        sessionManagement.adminRevokeAllSessions(userId);
    }

    @PutMapping("/device-limit")
    @RequirePermission("admin.user:update")
    public ApiResponse<SetDeviceLimitRequest> setDeviceLimit(
            @PathVariable UUID userId,
            @Valid @RequestBody SetDeviceLimitRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng"));
        user.setMaxDevices(request.maxDevices());
        userRepository.save(user);
        return ApiResponse.ok(new SetDeviceLimitRequest(user.getMaxDevices()));
    }
}
