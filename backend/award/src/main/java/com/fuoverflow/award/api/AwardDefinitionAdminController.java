package com.fuoverflow.award.api;

import com.fuoverflow.award.api.dto.AwardDefinitionResponse;
import com.fuoverflow.award.api.dto.UpdateAwardDefinitionRequest;
import com.fuoverflow.award.application.AwardDefinitionAdminService;
import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/awards/definitions")
@RequirePermission("admin.panel:access")
public class AwardDefinitionAdminController {
    private final AwardDefinitionAdminService adminService;

    public AwardDefinitionAdminController(AwardDefinitionAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    @RequirePermission("admin.panel:access")
    public ApiResponse<List<AwardDefinitionResponse>> list() {
        return ApiResponse.ok(adminService.listAll());
    }

    @PutMapping("/{id}")
    @RequirePermission("admin.panel:access")
    public ApiResponse<AwardDefinitionResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAwardDefinitionRequest request) {
        return ApiResponse.ok(adminService.update(id, request));
    }
}
