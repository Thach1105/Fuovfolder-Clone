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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableAspectJAutoProxy
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CookieAuthenticationFilter cookieAuthenticationFilter) throws Exception {
        http
                .cors(Customizer.withDefaults())
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
                                "/api/v1/auth/introspect",
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
                .addFilterBefore(cookieAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
