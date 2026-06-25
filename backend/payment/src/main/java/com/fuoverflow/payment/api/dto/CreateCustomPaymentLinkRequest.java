package com.fuoverflow.payment.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;

/**
 * Yêu cầu tạo link thanh toán với số tiền do người dùng tự nhập (nạp linh động).
 * Tối thiểu 1.000đ; không giới hạn trên ở tầng ứng dụng (PayOS có hạn mức riêng).
 */
public record CreateCustomPaymentLinkRequest(
        @NotNull @Min(value = 1000, message = "Số tiền nạp tối thiểu là 1.000đ") Integer amountVnd,
        @NotBlank @URL String returnUrl,
        @NotBlank @URL String cancelUrl
) {
}
