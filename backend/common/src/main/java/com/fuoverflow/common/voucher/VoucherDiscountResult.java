package com.fuoverflow.common.voucher;

import java.util.UUID;

public record VoucherDiscountResult(
        boolean valid,
        UUID voucherId,
        int discountPoints,
        int finalPoints,
        String message
) {
    public static VoucherDiscountResult invalid(String message) {
        return new VoucherDiscountResult(false, null, 0, 0, message);
    }

    public static VoucherDiscountResult success(UUID voucherId, int discountPoints, int finalPoints, String message) {
        return new VoucherDiscountResult(true, voucherId, discountPoints, finalPoints, message);
    }
}
