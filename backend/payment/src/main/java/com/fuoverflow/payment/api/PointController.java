package com.fuoverflow.payment.api;

import com.fuoverflow.award.application.PointsWalletService;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.payment.api.dto.PointBalanceResponse;
import com.fuoverflow.payment.support.AuthContext;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/points")
public class PointController {

    private final PointsWalletService walletService;

    public PointController(PointsWalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/balance")
    public ApiResponse<PointBalanceResponse> getPointBalance() {
        UUID userId = AuthContext.currentUserId();
        long balance = walletService.getBalance(userId);
        return ApiResponse.ok(new PointBalanceResponse(
                BigDecimal.valueOf(balance),
                Instant.now()
        ));
    }
}
