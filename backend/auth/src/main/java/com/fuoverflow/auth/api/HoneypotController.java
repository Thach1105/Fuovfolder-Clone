package com.fuoverflow.auth.api;

import com.fuoverflow.common.support.RateLimitService;
import com.fuoverflow.common.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
public class HoneypotController {

    private static final Logger log = LoggerFactory.getLogger(HoneypotController.class);

    private final RateLimitService rateLimitService;

    public HoneypotController(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @RequestMapping({
            "/api/v1/admin/config",
            "/api/v1/users/export",
            "/api/v1/debug/dump",
            "/api/internal/graphql"
    })
    public ResponseEntity<Void> honeypot(HttpServletRequest request) {
        String ip = ClientIpResolver.resolve(request);
        log.warn("Honeypot triggered: ip={} ua={} path={} method={}",
                ip, request.getHeader("User-Agent"),
                request.getRequestURI(), request.getMethod());
        rateLimitService.blockIp(ip, Duration.ofHours(24));
        return ResponseEntity.notFound().build();
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robotsTxt() {
        return """
                User-agent: *
                Disallow: /api/v1/admin/config
                Disallow: /api/v1/users/export
                Disallow: /api/v1/debug/dump
                Disallow: /api/internal/graphql
                Disallow: /api/v1/admin/
                Allow: /api/v1/forums
                Allow: /api/v1/threads
                Allow: /api/v1/source/catalog
                Allow: /api/v1/coursera/catalog
                """;
    }
}
