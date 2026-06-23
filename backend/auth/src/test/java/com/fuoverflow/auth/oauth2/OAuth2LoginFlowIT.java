package com.fuoverflow.auth.oauth2;

import com.fuoverflow.auth.OAuth2TestApplication;
import com.fuoverflow.auth.application.AuthService;
import com.fuoverflow.auth.application.OAuthIdentityLinker;
import com.fuoverflow.auth.application.OAuthSessionIssuer;
import com.fuoverflow.auth.domain.LinkedIdentity;
import com.fuoverflow.auth.domain.ProviderProfile;
import com.fuoverflow.auth.domain.TokenPair;
import com.fuoverflow.auth.exception.OAuthEmailNotVerifiedException;
import com.fuoverflow.common.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = OAuth2TestApplication.class)
@ActiveProfiles("test")
class OAuth2LoginFlowIT {

    @Autowired
    private OAuthAuthenticationSuccessHandler successHandler;

    @Autowired
    private OAuthAuthenticationFailureHandler failureHandler;

    @MockBean
    private OAuthIdentityLinker identityLinker;

    @MockBean
    private OAuthSessionIssuer sessionIssuer;

    @Test
    void success_newUser_setsCookiesAndRedirectsWithNewTrue() throws Exception {
        UUID userId = UUID.randomUUID();
        when(identityLinker.link(any(ProviderProfile.class))).thenReturn(new LinkedIdentity(userId, true, false));
        when(sessionIssuer.issue(eq(userId), any())).thenReturn(bundle());

        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(request(), response, auth(true));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeaders("Set-Cookie")).anyMatch(v -> v.contains("fuoverflow_at="));
        assertThat(response.getHeaders("Set-Cookie")).anyMatch(v -> v.contains("fuoverflow_rt="));
        assertThat(response.getRedirectedUrl()).contains("provider=google", "new=true", userId.toString());
    }

    @Test
    void success_existingUser_setsCookiesAndRedirectsWithNewFalse() throws Exception {
        UUID userId = UUID.randomUUID();
        when(identityLinker.link(any(ProviderProfile.class))).thenReturn(new LinkedIdentity(userId, false, false));
        when(sessionIssuer.issue(eq(userId), any())).thenReturn(bundle());

        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(request(), response, auth(false));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeaders("Set-Cookie")).anyMatch(v -> v.contains("fuoverflow_at="));
        assertThat(response.getHeaders("Set-Cookie")).anyMatch(v -> v.contains("fuoverflow_rt="));
        assertThat(response.getRedirectedUrl()).contains("provider=google", "new=false", userId.toString());
    }

    @Test
    void failure_emailNotVerified_redirectsWithStableCode() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        failureHandler.onAuthenticationFailure(
                request(),
                response,
                authException(new OAuthEmailNotVerifiedException("OAuth provider email_verified is false"))
        );

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).contains("code=OAUTH_EMAIL_NOT_VERIFIED");
    }

    @Test
    void failure_userBlocked_redirectsWithStableCode() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        failureHandler.onAuthenticationFailure(
                request(),
                response,
                authException(new ForbiddenException("USER_DISABLED", "User cannot authenticate"))
        );

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).contains("code=OAUTH_USER_BLOCKED");
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "JUnit");
        return request;
    }

    private TestingAuthenticationToken auth(boolean isNew) {
        String suffix = isNew ? "new" : "existing";
        PrincipalOAuth2User principal = new PrincipalOAuth2User(
                new org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser(
                        java.util.List.of(),
                        org.springframework.security.oauth2.core.oidc.OidcIdToken.withTokenValue("id-token")
                                .subject("google-sub-" + suffix)
                                .claim("email", suffix + "@example.com")
                                .claim("email_verified", true)
                                .claim("name", isNew ? "New User" : "Existing User")
                                .issuedAt(Instant.now())
                                .expiresAt(Instant.now().plusSeconds(3600))
                                .build()
                ),
                new ProviderProfile("google", "google-sub-" + suffix, suffix + "@example.com", true,
                        isNew ? "New User" : "Existing User", "https://example.com/avatar.jpg")
        );
        return new TestingAuthenticationToken(principal, null, "ROLE_USER");
    }

    private AuthenticationException authException(RuntimeException cause) {
        return new AuthenticationException("test-auth-failure", cause) {
        };
    }

    private AuthService.AuthTokenBundle bundle() {
        Instant now = Instant.now();
        TokenPair pair = new TokenPair(
                "access-token",
                "refresh-token",
                "hashed-refresh-token",
                UUID.randomUUID(),
                UUID.randomUUID(),
                now,
                now.plusSeconds(600),
                now.plusSeconds(3600)
        );
        return new AuthService.AuthTokenBundle(pair, null);
    }
}
