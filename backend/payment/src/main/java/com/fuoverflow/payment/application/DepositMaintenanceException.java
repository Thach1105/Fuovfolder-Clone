package com.fuoverflow.payment.application;

import com.fuoverflow.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class DepositMaintenanceException extends ApiException {
    public DepositMaintenanceException() {
        super("DEPOSIT_MAINTENANCE",
                "Hệ thống đang cập nhật dịch vụ thanh toán. Tính năng nạp tiền tạm thời được bảo trì. Vui lòng quay lại sau. Cảm ơn bạn đã thông cảm!",
                HttpStatus.SERVICE_UNAVAILABLE);
    }
}
