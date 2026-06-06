package com.fuoverflow.membership.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.membership.api.dto.AdminMembershipPlanResponse;
import com.fuoverflow.membership.api.dto.CreateMembershipPlanRequest;
import com.fuoverflow.membership.api.dto.MembershipRoleOptionResponse;
import com.fuoverflow.membership.api.dto.UpdateMembershipPlanRequest;
import com.fuoverflow.membership.application.MembershipPlanAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/admin/membership")
@RequirePermission("admin.panel:access")
public class MembershipAdminController {
    private final MembershipPlanAdminService planAdminService;

    public MembershipAdminController(MembershipPlanAdminService planAdminService) {
        this.planAdminService = planAdminService;
    }

    @GetMapping("/plans")
    @RequirePermission("membership.admin:read")
    public ApiResponse<List<AdminMembershipPlanResponse>> listPlans() {
        return ApiResponse.ok(planAdminService.listPlans());
    }

    @GetMapping("/plans/{planId}")
    @RequirePermission("membership.admin:read")
    public ApiResponse<AdminMembershipPlanResponse> getPlan(@PathVariable UUID planId) {
        return ApiResponse.ok(planAdminService.getPlan(planId));
    }

    @GetMapping("/roles")
    @RequirePermission("membership.admin:read")
    public ApiResponse<List<MembershipRoleOptionResponse>> listMembershipRoles() {
        return ApiResponse.ok(planAdminService.listMembershipRoles());
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("membership.admin:update")
    public ApiResponse<AdminMembershipPlanResponse> createPlan(@Valid @RequestBody CreateMembershipPlanRequest request) {
        return ApiResponse.ok(planAdminService.createPlan(request));
    }

    @PutMapping("/plans/{planId}")
    @RequirePermission("membership.admin:update")
    public ApiResponse<AdminMembershipPlanResponse> updatePlan(
            @PathVariable UUID planId,
            @Valid @RequestBody UpdateMembershipPlanRequest request) {
        return ApiResponse.ok(planAdminService.updatePlan(planId, request));
    }
}
