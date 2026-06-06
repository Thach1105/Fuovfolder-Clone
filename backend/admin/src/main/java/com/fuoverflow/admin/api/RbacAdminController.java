package com.fuoverflow.admin.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.user.api.dto.CreateRoleRequest;
import com.fuoverflow.user.api.dto.EffectivePermissions;
import com.fuoverflow.user.api.dto.PermissionCatalogResponse;
import com.fuoverflow.user.api.dto.RoleDetailResponse;
import com.fuoverflow.user.api.dto.RoleSummaryResponse;
import com.fuoverflow.user.api.dto.UpdateRolePermissionsRequest;
import com.fuoverflow.user.api.dto.UpdateUserOverridesRequest;
import com.fuoverflow.user.api.dto.UpdateUserRolesRequest;
import com.fuoverflow.user.api.dto.UserPermissionOverrideResponse;
import com.fuoverflow.user.application.RbacAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/rbac")
@RequirePermission("admin.panel:access")
public class RbacAdminController {
    private final RbacAdminService rbacAdminService;

    public RbacAdminController(RbacAdminService rbacAdminService) {
        this.rbacAdminService = rbacAdminService;
    }

    @GetMapping("/permissions")
    @RequirePermission("rbac.role:read")
    public ApiResponse<PermissionCatalogResponse> permissions() {
        return ApiResponse.ok(rbacAdminService.listPermissions());
    }

    @GetMapping("/roles")
    @RequirePermission("rbac.role:read")
    public ApiResponse<List<RoleSummaryResponse>> roles() {
        return ApiResponse.ok(rbacAdminService.listRoles());
    }

    @GetMapping("/roles/{roleId}")
    @RequirePermission("rbac.role:read")
    public ApiResponse<RoleDetailResponse> role(@PathVariable UUID roleId) {
        return ApiResponse.ok(rbacAdminService.getRole(roleId));
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("rbac.role:update")
    public ApiResponse<RoleDetailResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return ApiResponse.ok(rbacAdminService.createRole(request));
    }

    @PutMapping("/roles/{roleId}/permissions")
    @RequirePermission("rbac.role:update")
    public ApiResponse<RoleDetailResponse> updateRolePermissions(
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRolePermissionsRequest request) {
        return ApiResponse.ok(rbacAdminService.updateRolePermissions(roleId, request));
    }

    @GetMapping("/users/{userId}/roles")
    @RequirePermission("rbac.assignment:read")
    public ApiResponse<List<String>> userRoles(@PathVariable UUID userId) {
        return ApiResponse.ok(rbacAdminService.getUserRoleSlugs(userId));
    }

    @PutMapping("/users/{userId}/roles")
    @RequirePermission("rbac.assignment:update")
    public ApiResponse<List<String>> updateUserRoles(
            Authentication authentication,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRolesRequest request) {
        UUID actorId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(rbacAdminService.updateUserRoles(userId, request, actorId));
    }

    @GetMapping("/users/{userId}/permissions")
    @RequirePermission("rbac.user_override:read")
    public ApiResponse<EffectivePermissions> userPermissions(@PathVariable UUID userId) {
        return ApiResponse.ok(rbacAdminService.getEffectivePermissions(userId));
    }

    @GetMapping("/users/{userId}/overrides")
    @RequirePermission("rbac.user_override:read")
    public ApiResponse<List<UserPermissionOverrideResponse>> userOverrides(@PathVariable UUID userId) {
        return ApiResponse.ok(rbacAdminService.getUserOverrides(userId));
    }

    @PutMapping("/users/{userId}/overrides")
    @RequirePermission("rbac.user_override:update")
    public ApiResponse<List<UserPermissionOverrideResponse>> updateUserOverrides(
            Authentication authentication,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserOverridesRequest request) {
        UUID actorId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(rbacAdminService.updateUserOverrides(userId, request, actorId));
    }
}
