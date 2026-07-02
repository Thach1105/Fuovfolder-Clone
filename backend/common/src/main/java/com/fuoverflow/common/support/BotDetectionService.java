package com.fuoverflow.common.support;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class BotDetectionService {

    private static final List<String> BOT_PATTERNS = List.of(
            "curl", "wget", "python-requests", "python-urllib",
            "scrapy", "httpclient", "java/", "go-http-client",
            "node-fetch", "axios/", "postman", "insomnia",
            "httpie", "okhttp", "apache-httpclient"
    );

    private static final List<String> ALLOWED_BOTS = List.of(
            "googlebot", "bingbot", "slurp", "duckduckbot",
            "facebookexternalhit", "twitterbot", "linkedinbot",
            "telegrambot", "whatsapp", "applebot"
    );

    public BotScore evaluate(HttpServletRequest request) {
        int score = 0;
        List<String> signals = new ArrayList<>();

        String ua = request.getHeader("User-Agent");
        if (ua == null || ua.isBlank()) {
            score += 40;
            signals.add("missing-ua");
        } else {
            String lower = ua.toLowerCase(Locale.ROOT);
            if (ALLOWED_BOTS.stream().anyMatch(lower::contains)) {
                return new BotScore(0, List.of(), false, false);
            }
            if (BOT_PATTERNS.stream().anyMatch(lower::contains)) {
                score += 50;
                signals.add("known-bot-ua");
            }
        }

        if (request.getHeader("Accept-Language") == null) {
            score += 15;
            signals.add("missing-accept-language");
        }
        if (request.getHeader("Accept") == null) {
            score += 15;
            signals.add("missing-accept");
        }
        if (request.getHeader("Accept-Encoding") == null) {
            score += 5;
            signals.add("missing-accept-encoding");
        }

        return new BotScore(score, signals, score >= 80, score >= 50);
    }

    public record BotScore(int score, List<String> signals, boolean blocked, boolean suspicious) {}
}
