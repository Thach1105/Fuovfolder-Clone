package com.fuoverflow.voucher.api;

import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.voucher.api.dto.VoucherPreviewRequest;
import com.fuoverflow.voucher.api.dto.VoucherPreviewResponse;
import com.fuoverflow.voucher.application.VoucherService;
import com.fuoverflow.voucher.domain.VoucherDiscountResult;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vouchers")
public class VoucherController {
    private final VoucherService voucherService;

    public VoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @PostMapping("/preview")
    public ApiResponse<VoucherPreviewResponse> preview(
            Authentication authentication,
            @Valid @RequestBody VoucherPreviewRequest request) {
        UUID userId = UUID.fromString(authentication.getName());
        VoucherDiscountResult result = voucherService.preview(
                request.code(), userId, request.transactionType(), request.originalPoints());
        return ApiResponse.ok(new VoucherPreviewResponse(
                result.valid(), result.discountPoints(), result.finalPoints(), result.message()));
    }
}
