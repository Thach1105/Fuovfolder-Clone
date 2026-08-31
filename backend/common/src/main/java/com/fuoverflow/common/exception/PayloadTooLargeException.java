package com.fuoverflow.common.exception;

import org.springframework.http.HttpStatus;

public class PayloadTooLargeException extends ApiException {
    public PayloadTooLargeException(String code, String message) {
        super(code, message, HttpStatus.PAYLOAD_TOO_LARGE);
    }
}
