package com.fuoverflow.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment")
public record PaymentProperties(int linkExpiryMinutes, Boolean maintenanceEnabled) {
    public PaymentProperties {
        if (linkExpiryMinutes <= 0) linkExpiryMinutes = 30;
        if (maintenanceEnabled == null) maintenanceEnabled = true;
    }
}
