package com.fuoverflow.award.api;

import com.fuoverflow.award.api.dto.PointsBalanceResponse;
import com.fuoverflow.award.api.dto.PointsLedgerEntryResponse;
import com.fuoverflow.award.api.dto.PointsLedgerPageResponse;
import com.fuoverflow.award.application.PointsQueryService;
import com.fuoverflow.award.application.PointsWalletService;
import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/points")
public class PointsController {
    private final PointsWalletService walletService;
    private final PointsQueryService queryService;

    public PointsController(PointsWalletService walletService, PointsQueryService queryService) {
        this.walletService = walletService;
        this.queryService = queryService;
    }

    @GetMapping("/balance")
    @RequirePermission("points:read")
    public ApiResponse<PointsBalanceResponse> balance(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(new PointsBalanceResponse(walletService.getBalance(userId)));
    }

    @GetMapping("/ledger")
    @RequirePermission("points:read")
    public ApiResponse<PointsLedgerPageResponse> ledger(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(queryService.listLedger(userId, page, size));
    }
}
