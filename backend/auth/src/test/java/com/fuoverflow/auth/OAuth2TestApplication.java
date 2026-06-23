package com.fuoverflow.auth;

import com.fuoverflow.auth.application.CookieService;
import com.fuoverflow.auth.application.JwtService;
import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.config.CookieAuthenticationFilter;
import com.fuoverflow.auth.config.OAuth2Properties;
import com.fuoverflow.auth.config.SecurityConfig;
import com.fuoverflow.auth.oauth2.GoogleOAuth2UserService;
import com.fuoverflow.auth.oauth2.OAuthAuthenticationFailureHandler;
import com.fuoverflow.auth.oauth2.OAuthAuthenticationSuccessHandler;
import com.fuoverflow.auth.support.TokenGenerator;
import com.fuoverflow.auth.support.TokenHashing;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.time.Duration;

@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        MailSenderAutoConfiguration.class,
        ThymeleafAutoConfiguration.class
})
@Import({
        SecurityConfig.class,
        GoogleOAuth2UserService.class,
        OAuthAuthenticationSuccessHandler.class,
        OAuthAuthenticationFailureHandler.class
})
public class OAuth2TestApplication {

    @Bean
    @Primary
    ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration google = ClientRegistration.withRegistrationId("google")
                .clientId("test-client")
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .scope("openid", "email", "profile")
                .build();
        return registrationId -> "google".equals(registrationId) ? google : null;
    }

    @Bean
    @Primary
    AuthProperties authProperties() {
        return new AuthProperties(
                "fuoverflow",
                "fuoverflow-api",
                Duration.ofMinutes(10),
                Duration.ofDays(30),
                "test-pepper",
                new AuthProperties.Cookie(false, "Lax", "fuoverflow_at", "fuoverflow_rt"),
                new AuthProperties.Jwt("test-key"),
                new AuthProperties.EmailVerification(false, null, null, null),
                new AuthProperties.PasswordReset(false, null, null, null, Duration.ofHours(1))
        );
    }

    @Bean
    @Primary
    OAuth2Properties oauth2Properties() {
        return new OAuth2Properties(
                "http://localhost:5173/auth/callback",
                "http://localhost:5173/auth/error",
                "http://localhost:5173"
        );
    }

    @Bean
    @Primary
    JwtService jwtService() {
        try {
            return new JwtService(authProperties(), new TokenGenerator(), new TokenHashing(authProperties()));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create test JwtService", exception);
        }
    }

    @Bean
    @Primary
    CookieService cookieService() {
        return new CookieService(authProperties());
    }

    @Bean
    @Primary
    CookieAuthenticationFilter cookieAuthenticationFilter() {
        return new CookieAuthenticationFilter(authProperties(), jwtService());
    }
}
