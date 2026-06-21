package com.fuoverflow.auth.config;

import com.fuoverflow.auth.application.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SecurityConfigTest.TestApplication.class)
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void webhookEndpoint_shouldBeAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(post("/api/v1/payment/payos/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void randomEndpoint_shouldStillRequireAuth() throws Exception {
        mockMvc.perform(get("/api/v1/secure-probe"))
                .andExpect(status().isUnauthorized());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
    })
    @Import({SecurityConfig.class, TestController.class})
    static class TestApplication {
        @Bean
        CookieAuthenticationFilter cookieAuthenticationFilter() {
            return new CookieAuthenticationFilter(authProperties(), mock(JwtService.class));
        }

        @Bean
        AuthProperties authProperties() {
            return new AuthProperties(
                    "test-issuer",
                    "test-audience",
                    java.time.Duration.ofMinutes(15),
                    java.time.Duration.ofDays(7),
                    "pepper",
                    new AuthProperties.Cookie(false, "Lax", "access_token", "refresh_token"),
                    new AuthProperties.Jwt("test-key"),
                    new AuthProperties.EmailVerification(false, "noreply@example.com", "http://localhost/verify", "Verify"),
                    new AuthProperties.PasswordReset(false, "noreply@example.com", "http://localhost/reset", "Reset", java.time.Duration.ofHours(1))
            );
        }
    }

    @RestController
    static class TestController {
        @PostMapping("/api/v1/payment/payos/webhook")
        void webhook(@RequestBody String body) {
        }

        @GetMapping("/api/v1/secure-probe")
        void secureProbe() {
        }
    }
}
