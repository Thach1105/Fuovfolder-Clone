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
        long thresholdVnd = toLong(config.getOrDefault("thresholdVnd", 0));
        long amountVnd = toLong(eventData.getOrDefault("amountVnd", 0));

        if (amountVnd < thresholdVnd) {
            return null;
        }

        Object value = eventData.getOrDefault("displayName", "Một thành viên");
        String displayName = value instanceof String s ? s : "Một thành viên";
        String template = (String) config.getOrDefault("messageTemplate",
                "%s vừa nạp %,d VND vào tài khoản!");
        return String.format(template, displayName, amountVnd);
    }

    private long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0L; }
        }
        return 0L;
    }
}
