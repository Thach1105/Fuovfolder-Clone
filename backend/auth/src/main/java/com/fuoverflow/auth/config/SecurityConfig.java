package com.fuoverflow.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fuoverflow.common.config.RateLimitProperties;

import com.fuoverflow.auth.oauth2.GoogleOAuth2UserService;
import com.fuoverflow.auth.oauth2.OAuthAuthenticationSuccessHandler;
import com.fuoverflow.auth.oauth2.OAuthAuthenticationFailureHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableAspectJAutoProxy
@EnableConfigurationProperties({AuthProperties.class, OAuth2Properties.class, RateLimitProperties.class})
public class SecurityConfig {
    private final CookieAuthenticationFilter cookieAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final BotDetectionFilter botDetectionFilter;
    private final GoogleOAuth2UserService googleOAuth2UserService;
    private final OAuthAuthenticationSuccessHandler oauthSuccessHandler;
    private final OAuthAuthenticationFailureHandler oauthFailureHandler;
    private final AuthProperties authProperties;

    public SecurityConfig(CookieAuthenticationFilter cookieAuthenticationFilter,
                         RateLimitFilter rateLimitFilter,
                         BotDetectionFilter botDetectionFilter,
                         GoogleOAuth2UserService googleOAuth2UserService,
                         OAuthAuthenticationSuccessHandler oauthSuccessHandler,
                         OAuthAuthenticationFailureHandler oauthFailureHandler,
                         AuthProperties authProperties) {
        this.cookieAuthenticationFilter = cookieAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.botDetectionFilter = botDetectionFilter;
        this.googleOAuth2UserService = googleOAuth2UserService;
        this.oauthSuccessHandler = oauthSuccessHandler;
        this.oauthFailureHandler = oauthFailureHandler;
        this.authProperties = authProperties;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // CSRF disabled: access token sent via Authorization header or HttpOnly cookie with SameSite=Lax.
                // SameSite=Lax prevents cross-origin POST requests from sending cookies.
                // All state-change operations use POST/PUT/DELETE (never GET).
                // If SameSite is changed to None, CSRF protection MUST be re-enabled.
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(31536000)
                                .includeSubDomains(true))
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' https:"))
                        .referrerPolicy(ref -> ref
                                .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicy(pp -> pp
                                .policy("geolocation=(), camera=(), microphone=()")))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/robots.txt",
                                "/api/v1/admin/config",
                                "/api/v1/users/export",
                                "/api/v1/debug/dump",
                                "/api/internal/graphql",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/email/verify",
                                "/api/v1/auth/email/verify-code",
                                "/api/v1/auth/email/resend",
                                "/api/v1/auth/password/forgot",
                                "/api/v1/auth/password/reset",
                                "/api/v1/auth/password/set",
                                "/oauth2/authorization/google",
                                "/login/oauth2/code/google",
                                "/api/v1/payment/payos/webhook",
                                "/api/v1/exam/webhook/papers",
                                "/api/v1/public/data",
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/coursera/catalog", "/api/v1/coursera/catalog/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/push/vapid-public-key")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/forums", "/api/v1/forums/**",
                                "/api/v1/threads", "/api/v1/threads/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/source/catalog", "/api/v1/source/catalog/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/source/media/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/exam/catalog", "/api/v1/exam/catalog/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/exam/media/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/membership/plans")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/broadcasts/stream",
                                "/api/v1/announcements/active")
                        .permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(auth -> auth
                                .authorizationRequestRepository(
                                        new CookieOAuth2AuthorizationRequestRepository(authProperties)))
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(googleOAuth2UserService))
                        .successHandler(oauthSuccessHandler)
                        .failureHandler(oauthFailureHandler))
                .addFilterBefore(cookieAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, CookieAuthenticationFilter.class)
                .addFilterBefore(botDetectionFilter, RateLimitFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
