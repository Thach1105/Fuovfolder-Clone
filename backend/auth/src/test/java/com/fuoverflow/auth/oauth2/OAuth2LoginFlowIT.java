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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = OAuth2TestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuth2LoginFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OAuthIdentityLinker identityLinker;

    @MockBean
    private OAuthSessionIssuer sessionIssuer;

    @Test
    void oauthAuthenticatedRequest_newUser_setsCookiesAndRedirectsWithNewTrue() throws Exception {
        UUID userId = UUID.randomUUID();
        when(identityLinker.link(any(ProviderProfile.class))).thenReturn(new LinkedIdentity(userId, true, false));
        when(sessionIssuer.issue(eq(userId), any())).thenReturn(bundle());

        mockMvc.perform(get("/__test/oauth/success")
                        .with(authentication(auth(true))))
                .andExpect(status().is3xxRedirection())
                .andExpect(cookie().exists("fuoverflow_at"))
                .andExpect(cookie().exists("fuoverflow_rt"))
                .andExpect(header().string("Location", containsString("provider=google")))
                .andExpect(header().string("Location", containsString("new=true")))
                .andExpect(header().string("Location", containsString(userId.toString())));
    }

    @Test
    void oauthAuthenticatedRequest_existingUser_setsCookiesAndRedirectsWithNewFalse() throws Exception {
        UUID userId = UUID.randomUUID();
        when(identityLinker.link(any(ProviderProfile.class))).thenReturn(new LinkedIdentity(userId, false, false));
        when(sessionIssuer.issue(eq(userId), any())).thenReturn(bundle());

        mockMvc.perform(get("/__test/oauth/success")
                        .with(authentication(auth(false))))
                .andExpect(status().is3xxRedirection())
                .andExpect(cookie().exists("fuoverflow_at"))
                .andExpect(cookie().exists("fuoverflow_rt"))
                .andExpect(header().string("Location", containsString("new=false")))
                .andExpect(header().string("Location", containsString(userId.toString())));
    }

    @Test
    void oauthAuthenticatedRequest_emailNotVerified_redirectsToErrorPage() throws Exception {
        when(identityLinker.link(any(ProviderProfile.class)))
                .thenThrow(new OAuthEmailNotVerifiedException("OAuth provider email_verified is false"));

        mockMvc.perform(get("/__test/oauth/failure/email-not-verified")
                        .with(authentication(auth(false))))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("code=OAUTH_EMAIL_NOT_VERIFIED")));
    }

    @Test
    void oauthAuthenticatedRequest_userBlocked_redirectsToErrorPage() throws Exception {
        UUID userId = UUID.randomUUID();
        when(identityLinker.link(any(ProviderProfile.class))).thenReturn(new LinkedIdentity(userId, false, false));
        when(sessionIssuer.issue(eq(userId), any()))
                .thenThrow(new ForbiddenException("USER_DISABLED", "User cannot authenticate"));

        mockMvc.perform(get("/__test/oauth/failure/user-blocked")
                        .with(authentication(auth(false))))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("code=OAUTH_USER_BLOCKED")));
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
