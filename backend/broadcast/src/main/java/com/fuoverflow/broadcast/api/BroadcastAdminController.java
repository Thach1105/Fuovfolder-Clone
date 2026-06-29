package com.fuoverflow.broadcast.api;

import com.fuoverflow.broadcast.api.dto.BroadcastConfigRequest;
import com.fuoverflow.broadcast.api.dto.BroadcastConfigResponse;
import com.fuoverflow.broadcast.application.BroadcastConfigService;
import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/broadcasts/configs")
@RequirePermission("admin.panel:access")
public class BroadcastAdminController {

    private final BroadcastConfigService configService;

    public BroadcastAdminController(BroadcastConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    @RequirePermission("broadcast.admin:read")
    public ApiResponse<List<BroadcastConfigResponse>> list() {
        return ApiResponse.ok(configService.listAll());
    }

    @GetMapping("/{eventType}")
    @RequirePermission("broadcast.admin:read")
    public ApiResponse<BroadcastConfigResponse> get(@PathVariable String eventType) {
        return ApiResponse.ok(configService.getByEventType(eventType));
    }

    @PutMapping("/{eventType}")
    @RequirePermission("broadcast.admin:update")
    public ApiResponse<BroadcastConfigResponse> upsert(
            @PathVariable String eventType,
            @Valid @RequestBody BroadcastConfigRequest request) {
        return ApiResponse.ok(configService.upsert(eventType, request));
    }

    @PatchMapping("/{eventType}/toggle")
    @RequirePermission("broadcast.admin:update")
    public ApiResponse<BroadcastConfigResponse> toggle(@PathVariable String eventType) {
        return ApiResponse.ok(configService.toggle(eventType));
    }
}
