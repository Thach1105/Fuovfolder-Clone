package com.fuoverflow.voucher.api.dto;

public record VoucherPreviewResponse(
        boolean valid,
        int discountPoints,
        int finalPoints,
        String message
) {
}
