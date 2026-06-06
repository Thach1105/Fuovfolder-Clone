package com.fuoverflow.source.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.source.api.dto.CreatePurchaseRequest;
import com.fuoverflow.source.api.dto.PurchasePageResponse;
import com.fuoverflow.source.api.dto.PurchaseResponse;
import com.fuoverflow.source.api.dto.PurchaseStatsResponse;
import com.fuoverflow.source.application.SourcePurchaseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/source/purchases")
public class SourcePurchaseController {
    private final SourcePurchaseService purchaseService;

    public SourcePurchaseController(SourcePurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("source.purchase:create")
    public ApiResponse<PurchaseResponse> purchase(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePurchaseRequest body) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(purchaseService.purchase(userId, body.catalogItemId(), idempotencyKey));
    }

    @GetMapping
    @RequirePermission("source.purchase:read")
    public ApiResponse<PurchasePageResponse> list(
            Authentication authentication,
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(purchaseService.listMine(userId, filter, page, size));
    }

    @GetMapping("/stats")
    @RequirePermission("source.purchase:read")
    public ApiResponse<PurchaseStatsResponse> stats(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(purchaseService.stats(userId));
    }

    @GetMapping("/{id}")
    @RequirePermission("source.purchase:read")
    public ApiResponse<PurchaseResponse> get(Authentication authentication, @PathVariable UUID id) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(purchaseService.getMine(userId, id));
    }
}
