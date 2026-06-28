package com.fuoverflow.payment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;

@Configuration
@EnableConfigurationProperties({PayOSProperties.class, PaymentProperties.class})
public class PayOSClientConfig {

    @Bean
    public PayOS payOS(PayOSProperties properties) {
        return new PayOS(properties.clientId(), properties.apiKey(), properties.checksumKey());
    }
}
