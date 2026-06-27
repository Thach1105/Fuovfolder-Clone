package com.fuoverflow.admin.api;

import com.fuoverflow.admin.application.UserDeletionService;
import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.user.api.dto.AdminOverviewResponse;
import com.fuoverflow.user.api.dto.AdminUserPageResponse;
import com.fuoverflow.user.application.UserAdminService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequirePermission("admin.panel:access")
public class AdminController {
    private final UserAdminService userAdminService;
    private final UserDeletionService userDeletionService;

    public AdminController(UserAdminService userAdminService, UserDeletionService userDeletionService) {
        this.userAdminService = userAdminService;
        this.userDeletionService = userDeletionService;
    }

    @GetMapping("/overview")
    @RequirePermission("admin.overview:read")
    public ApiResponse<AdminOverviewResponse> overview() {
        return ApiResponse.ok(userAdminService.getOverview());
    }

    @GetMapping("/users")
    @RequirePermission("admin.user:read")
    public ApiResponse<AdminUserPageResponse> users(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(userAdminService.listUsers(page, size));
    }

    @DeleteMapping("/users/{userId}")
    @RequirePermission("admin.user:delete")
    public ApiResponse<Void> deleteUser(@PathVariable UUID userId, Authentication authentication) {
        UUID adminId = UUID.fromString(authentication.getName());
        userDeletionService.softDeleteUser(userId, adminId);
        return ApiResponse.ok(null);
    }
}
