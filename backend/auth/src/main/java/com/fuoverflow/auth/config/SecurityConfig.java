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

import com.fuoverflow.auth.oauth2.GoogleOAuth2UserService;
import com.fuoverflow.auth.oauth2.OAuthAuthenticationSuccessHandler;
import com.fuoverflow.auth.oauth2.OAuthAuthenticationFailureHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableAspectJAutoProxy
@EnableConfigurationProperties({AuthProperties.class, OAuth2Properties.class})
public class SecurityConfig {
    private final CookieAuthenticationFilter cookieAuthenticationFilter;
    private final GoogleOAuth2UserService googleOAuth2UserService;
    private final OAuthAuthenticationSuccessHandler oauthSuccessHandler;
    private final OAuthAuthenticationFailureHandler oauthFailureHandler;
    private final AuthProperties authProperties;

    public SecurityConfig(CookieAuthenticationFilter cookieAuthenticationFilter,
                         GoogleOAuth2UserService googleOAuth2UserService,
                         OAuthAuthenticationSuccessHandler oauthSuccessHandler,
                         OAuthAuthenticationFailureHandler oauthFailureHandler,
                         AuthProperties authProperties) {
        this.cookieAuthenticationFilter = cookieAuthenticationFilter;
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
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/email/verify",
                                "/api/v1/auth/password/forgot",
                                "/api/v1/auth/password/reset",
                                "/api/v1/auth/introspect",
                                "/oauth2/authorization/google",
                                "/login/oauth2/code/google",
                                "/api/v1/payment/payos/webhook",
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
                        .requestMatchers(HttpMethod.GET, "/api/v1/membership/plans")
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
                .addFilterBefore(cookieAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
