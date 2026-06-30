package com.fuoverflow.common.voucher;

import java.util.UUID;

public interface VoucherRedemptionPort {
    VoucherDiscountResult preview(String code, UUID userId, String transactionType, int originalPoints);
    VoucherDiscountResult redeem(String code, UUID userId, String transactionType, UUID transactionId, int originalPoints);
}
