package com.fuoverflow.award.api;

import com.fuoverflow.award.api.dto.AdjustPointsRequest;
import com.fuoverflow.award.api.dto.PointsLedgerEntryResponse;
import com.fuoverflow.award.application.PointsWalletService;
import com.fuoverflow.award.persistence.PointsLedgerEntity;
import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequirePermission("admin.panel:access")
public class AdminPointsController {
    private final PointsWalletService walletService;

    public AdminPointsController(PointsWalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/{userId}/points/adjust")
    @RequirePermission("points.admin:update")
    public ApiResponse<PointsLedgerEntryResponse> adjust(
            Authentication authentication,
            @PathVariable UUID userId,
            @Valid @RequestBody AdjustPointsRequest request) {
        UUID actorId = UUID.fromString(authentication.getName());
        PointsLedgerEntity entry = walletService.adjust(userId, request.delta(), request.reason(), actorId);
        return ApiResponse.ok(toEntry(entry));
    }

    private PointsLedgerEntryResponse toEntry(PointsLedgerEntity e) {
        return new PointsLedgerEntryResponse(
                e.getId(),
                e.getDelta(),
                e.getReason(),
                e.getSourceType(),
                e.getSourceId(),
                e.getCreatedAt());
    }
}
