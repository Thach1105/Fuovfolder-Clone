package com.fuoverflow.voucher.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.voucher.api.dto.AssignUsersRequest;
import com.fuoverflow.voucher.api.dto.CreateVoucherRequest;
import com.fuoverflow.voucher.api.dto.UpdateVoucherRequest;
import com.fuoverflow.voucher.api.dto.VoucherRedemptionResponse;
import com.fuoverflow.voucher.api.dto.VoucherResponse;
import com.fuoverflow.voucher.application.VoucherAdminService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/vouchers")
@RequirePermission("admin.panel:access")
public class VoucherAdminController {
    private final VoucherAdminService adminService;

    public VoucherAdminController(VoucherAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    @RequirePermission("voucher.admin:read")
    public ApiResponse<Page<VoucherResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.listAll(page, size));
    }

    @GetMapping("/{id}")
    @RequirePermission("voucher.admin:read")
    public ApiResponse<VoucherResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(adminService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("voucher.admin:create")
    public ApiResponse<VoucherResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateVoucherRequest request) {
        UUID createdBy = UUID.fromString(authentication.getName());
        return ApiResponse.ok(adminService.create(
                request.code(), request.description(), request.discountType(),
                request.discountValue(), request.maxDiscountPoints(), request.minOrderPoints(),
                request.maxUsage(), request.maxUsagePerUser(), request.applicableTypes(),
                request.requiredMembershipSlugs(), request.startsAt(), request.endsAt(), createdBy));
    }

    @PutMapping("/{id}")
    @RequirePermission("voucher.admin:update")
    public ApiResponse<VoucherResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVoucherRequest request) {
        return ApiResponse.ok(adminService.update(
                id, request.code(), request.description(), request.discountType(),
                request.discountValue(), request.maxDiscountPoints(), request.minOrderPoints(),
                request.maxUsage(), request.maxUsagePerUser(), request.applicableTypes(),
                request.requiredMembershipSlugs(), request.startsAt(), request.endsAt()));
    }

    @PatchMapping("/{id}/toggle")
    @RequirePermission("voucher.admin:update")
    public ApiResponse<VoucherResponse> toggle(@PathVariable UUID id) {
        return ApiResponse.ok(adminService.toggleActive(id));
    }

    @PostMapping("/{id}/assignments")
    @RequirePermission("voucher.admin:update")
    public ApiResponse<Void> assignUsers(
            @PathVariable UUID id,
            @Valid @RequestBody AssignUsersRequest request) {
        adminService.assignUsers(id, request.userIds());
        return ApiResponse.ok(null, "Users assigned");
    }

    @DeleteMapping("/{id}/assignments/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission("voucher.admin:update")
    public void removeAssignment(@PathVariable UUID id, @PathVariable UUID userId) {
        adminService.removeAssignment(id, userId);
    }

    @GetMapping("/{id}/redemptions")
    @RequirePermission("voucher.admin:read")
    public ApiResponse<Page<VoucherRedemptionResponse>> redemptions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.listRedemptions(id, page, size));
    }
}
