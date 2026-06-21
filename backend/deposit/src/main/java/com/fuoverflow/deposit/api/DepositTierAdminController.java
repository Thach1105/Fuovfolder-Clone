package com.fuoverflow.deposit.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.deposit.api.dto.AdminDepositTierResponse;
import com.fuoverflow.deposit.api.dto.CreateDepositTierRequest;
import com.fuoverflow.deposit.api.dto.UpdateDepositTierRequest;
import com.fuoverflow.deposit.application.DepositTierAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/v1/admin/deposit-tiers")
@RequirePermission("admin.panel:access")
public class DepositTierAdminController {

    private final DepositTierAdminService service;

    public DepositTierAdminController(DepositTierAdminService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("deposit.admin:read")
    public ApiResponse<List<AdminDepositTierResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{tierId}")
    @RequirePermission("deposit.admin:read")
    public ApiResponse<AdminDepositTierResponse> get(@PathVariable UUID tierId) {
        return ApiResponse.ok(service.get(tierId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("deposit.admin:update")
    public ApiResponse<AdminDepositTierResponse> create(@Valid @RequestBody CreateDepositTierRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{tierId}")
    @RequirePermission("deposit.admin:update")
    public ApiResponse<AdminDepositTierResponse> update(@PathVariable UUID tierId,
                                                       @Valid @RequestBody UpdateDepositTierRequest request) {
        return ApiResponse.ok(service.update(tierId, request));
    }

    @PatchMapping("/{tierId}/toggle")
    @RequirePermission("deposit.admin:update")
    public ApiResponse<AdminDepositTierResponse> toggle(@PathVariable UUID tierId) {
        return ApiResponse.ok(service.toggle(tierId));
    }
}
