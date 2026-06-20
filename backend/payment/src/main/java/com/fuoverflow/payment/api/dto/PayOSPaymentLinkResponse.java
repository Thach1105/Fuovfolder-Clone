package com.fuoverflow.payment.api.dto;

public record PayOSPaymentLinkResponse(String checkoutUrl, String qrCode, String orderCode) {}
