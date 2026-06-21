package com.fuoverflow.payment.api;

import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.payment.application.PointService;
import com.fuoverflow.payment.api.dto.*;
import com.fuoverflow.payment.persistence.PointBalanceEntity;
import com.fuoverflow.payment.support.AuthContext;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/points")
public class PointController {

    private final PointService pointService;

    public PointController(PointService pointService) {
        this.pointService = pointService;
    }

    @GetMapping("/balance")
    public ApiResponse<PointBalanceResponse> getPointBalance() {
        UUID userId = AuthContext.currentUserId();
        PointBalanceEntity balance = pointService.getOrCreateBalance(userId);
        return ApiResponse.ok(new PointBalanceResponse(
                BigDecimal.valueOf(balance.getBalancePoints()),
                balance.getUpdatedAt()
        ));
    }

    @GetMapping("/transactions")
    public ApiResponse<PointTransactionResponse[]> getPointTransactions() {
        UUID userId = AuthContext.currentUserId();
        return ApiResponse.ok(pointService.getTransactions(userId).stream()
                .map(t -> new PointTransactionResponse(
                        t.getId().toString(),
                        BigDecimal.valueOf(t.getAmountPoints()),
                        t.getDirection(),
                        t.getType(),
                        t.getDescription(),
                        t.getCreatedAt()
                ))
                .toArray(PointTransactionResponse[]::new));
    }
}
