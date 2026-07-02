package com.fuoverflow.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.config.RateLimitProperties;
import com.fuoverflow.common.support.BotDetectionService;
import com.fuoverflow.common.support.BotDetectionService.BotScore;
import com.fuoverflow.common.support.RateLimitService;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.common.web.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
public class BotDetectionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BotDetectionFilter.class);

    private final BotDetectionService botDetectionService;
    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public BotDetectionFilter(BotDetectionService botDetectionService,
                              RateLimitService rateLimitService,
                              RateLimitProperties properties,
                              ObjectMapper objectMapper) {
        this.botDetectionService = botDetectionService;
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

        BotScore score = botDetectionService.evaluate(request);

        if (score.blocked()) {
            String ip = ClientIpResolver.resolve(request);
            log.warn("Bot blocked: ip={} score={} signals={} ua={} path={}",
                    ip, score.score(), score.signals(),
                    request.getHeader("User-Agent"), request.getRequestURI());
            rateLimitService.blockIp(ip, Duration.ofHours(1));

            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiResponse<?> body = ApiResponse.failure("BOT_DETECTED", "Access denied.", null);
            objectMapper.writeValue(response.getOutputStream(), body);
            return;
        }

        if (score.suspicious()) {
            request.setAttribute(RateLimitFilter.BOT_SCORE_ATTR, score.score());
        }

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator/")
                || uri.equals("/api/v1/payment/payos/webhook");
    }
}
