package com.fuoverflow.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.config.RateLimitProperties;
import com.fuoverflow.common.support.RateLimitService;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.common.web.ClientIpResolver;
import com.fuoverflow.common.web.EndpointTierClassifier;
import com.fuoverflow.common.web.EndpointTierClassifier.Tier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    static final String BOT_SCORE_ATTR = "fuexam.botScore";

    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitService rateLimitService,
                           RateLimitProperties properties,
                           ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!properties.enabled()) {
            chain.doFilter(request, response);
            return;
        }

        String ip = ClientIpResolver.resolve(request);

        if (rateLimitService.isBlocked(ip)) {
            writeJsonResponse(response, HttpStatus.FORBIDDEN,
                    "IP_BLOCKED", "Access denied.");
            return;
        }

        Tier tier = EndpointTierClassifier.classify(request.getMethod(), request.getRequestURI());
        RateLimitProperties.TierLimits limits = resolveLimits(tier);
        if (limits == null) {
            chain.doFilter(request, response);
            return;
        }

        int maxRequests = limits.anonymousMax();
        Integer botScore = (Integer) request.getAttribute(BOT_SCORE_ATTR);
        if (botScore != null && botScore > 0) {
            maxRequests = Math.max(1, maxRequests / 2);
        }

        String key = "ratelimit:" + tier.name().toLowerCase() + ":" + ip;

        if (!rateLimitService.isAllowed(key, maxRequests, limits.window())) {
            rateLimitService.recordViolation(ip);
            response.setHeader("Retry-After", String.valueOf(limits.window().toSeconds()));
            writeJsonResponse(response, HttpStatus.TOO_MANY_REQUESTS,
                    "RATE_LIMIT_EXCEEDED", "Too many requests. Please try again later.");
            return;
        }

        long remaining = rateLimitService.getRemainingRequests(key, maxRequests, limits.window());
        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset",
                String.valueOf(Instant.now().plus(limits.window()).getEpochSecond()));

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator/")
                || uri.equals("/api/v1/payment/payos/webhook")
                || uri.startsWith("/uploads/");
    }

    private RateLimitProperties.TierLimits resolveLimits(Tier tier) {
        return switch (tier) {
            case AUTH -> properties.auth();
            case PUBLIC -> properties.publicEndpoints();
            case API -> properties.api();
            case GLOBAL -> properties.global();
        };
    }

    private void writeJsonResponse(HttpServletResponse response, HttpStatus status,
                                   String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<?> body = ApiResponse.failure(code, message, null);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
