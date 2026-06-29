package com.fuoverflow.broadcast.application;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DepositBroadcastEvaluator implements BroadcastEvaluator {

    static final String EVENT_TYPE = "deposit.completed";

    @PostConstruct
    void register() {
        BroadcastEvaluatorFactory.register(EVENT_TYPE, this);
    }

    @Override
    public String evaluate(Map<String, Object> config, Map<String, Object> eventData) {
        long thresholdVnd = ((Number) config.getOrDefault("thresholdVnd", 0)).longValue();
        long amountVnd = ((Number) eventData.getOrDefault("amountVnd", 0)).longValue();

        if (amountVnd < thresholdVnd) {
            return null;
        }

        String displayName = (String) eventData.getOrDefault("displayName", "Một thành viên");
        String template = (String) config.getOrDefault("messageTemplate",
                "%s vừa nạp %,d VND vào tài khoản!");
        return String.format(template, displayName, amountVnd);
    }
}
