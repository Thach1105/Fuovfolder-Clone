package com.fuoverflow.auth.oauth2;

import com.fuoverflow.auth.api.dto.AuthTokenResponse;
import com.fuoverflow.auth.application.AuthService;
import com.fuoverflow.auth.application.CookieService;
import com.fuoverflow.auth.application.OAuthIdentityLinker;
import com.fuoverflow.auth.application.OAuthSessionIssuer;
import com.fuoverflow.auth.config.OAuth2Properties;
import com.fuoverflow.auth.domain.ClientContext;
import com.fuoverflow.auth.domain.LinkedIdentity;
import com.fuoverflow.auth.domain.ProviderProfile;
import com.fuoverflow.auth.domain.TokenPair;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthAuthenticationSuccessHandlerTest {

    @Mock private OAuthIdentityLinker identityLinker;
    @Mock private OAuthSessionIssuer sessionIssuer;
    @Mock private CookieService cookieService;
    @Mock private OAuth2Properties oauth2Properties;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private Authentication authentication;
    @Mock private OidcUser oidcUser;

    private OAuthAuthenticationSuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuthAuthenticationSuccessHandler(identityLinker, sessionIssuer, cookieService, oauth2Properties);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("Test Browser");
        when(oauth2Properties.successRedirect()).thenReturn("http://localhost:3000/oauth/callback");
    }

    @Test
    void shouldHandleSuccessFlowWithNewUser() throws Exception {
        UUID userId = UUID.randomUUID();
        ProviderProfile profile = new ProviderProfile("google", "123", "test@example.com", true, "Test User", null);
        PrincipalOAuth2User principal = new PrincipalOAuth2User(oidcUser, profile);
        LinkedIdentity linked = new LinkedIdentity(userId, true, false);
        TokenPair tokenPair = new TokenPair("access-token", "refresh-token", "hash",
                UUID.randomUUID(), UUID.randomUUID(), Instant.now(),
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(604800));
        AuthTokenResponse mockResponse = mock(AuthTokenResponse.class);
        AuthService.AuthTokenBundle bundle = new AuthService.AuthTokenBundle(tokenPair, mockResponse);

        when(authentication.getPrincipal()).thenReturn(principal);
        when(identityLinker.link(profile)).thenReturn(linked);
        when(sessionIssuer.issue(eq(userId), any(ClientContext.class))).thenReturn(bundle);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(identityLinker).link(profile);
        verify(cookieService).writeTokenCookies(response, tokenPair);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());

        String redirectUrl = redirectCaptor.getValue();
        assertThat(redirectUrl).contains("http://localhost:3000/oauth/callback");
        assertThat(redirectUrl).contains("provider=google");
        assertThat(redirectUrl).contains("new=true");
        assertThat(redirectUrl).contains("userId=" + userId);
    }

    @Test
    void shouldHandleSuccessFlowWithExistingUser() throws Exception {
        UUID userId = UUID.randomUUID();
        ProviderProfile profile = new ProviderProfile("google", "123", "test@example.com", true, "Test User", null);
        PrincipalOAuth2User principal = new PrincipalOAuth2User(oidcUser, profile);
        LinkedIdentity linked = new LinkedIdentity(userId, false, true);
        TokenPair tokenPair = new TokenPair("access-token", "refresh-token", "hash",
                UUID.randomUUID(), UUID.randomUUID(), Instant.now(),
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(604800));
        AuthTokenResponse mockResponse = mock(AuthTokenResponse.class);
        AuthService.AuthTokenBundle bundle = new AuthService.AuthTokenBundle(tokenPair, mockResponse);

        when(authentication.getPrincipal()).thenReturn(principal);
        when(identityLinker.link(profile)).thenReturn(linked);
        when(sessionIssuer.issue(eq(userId), any(ClientContext.class))).thenReturn(bundle);

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());

        String redirectUrl = redirectCaptor.getValue();
        assertThat(redirectUrl).contains("new=false");
    }

    @Test
    void shouldWriteCookies() throws Exception {
        UUID userId = UUID.randomUUID();
        ProviderProfile profile = new ProviderProfile("google", "123", "test@example.com", true, "Test User", null);
        PrincipalOAuth2User principal = new PrincipalOAuth2User(oidcUser, profile);
        LinkedIdentity linked = new LinkedIdentity(userId, true, false);
        TokenPair tokenPair = new TokenPair("access-token", "refresh-token", "hash",
                UUID.randomUUID(), UUID.randomUUID(), Instant.now(),
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(604800));
        AuthTokenResponse mockResponse = mock(AuthTokenResponse.class);
        AuthService.AuthTokenBundle bundle = new AuthService.AuthTokenBundle(tokenPair, mockResponse);

        when(authentication.getPrincipal()).thenReturn(principal);
        when(identityLinker.link(profile)).thenReturn(linked);
        when(sessionIssuer.issue(eq(userId), any(ClientContext.class))).thenReturn(bundle);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(cookieService).writeTokenCookies(response, tokenPair);
    }

    @Test
    void shouldConstructRedirectUrlWithUriComponentsBuilder() throws Exception {
        UUID userId = UUID.randomUUID();
        ProviderProfile profile = new ProviderProfile("google", "123", "test@example.com", true, "Test User", null);
        PrincipalOAuth2User principal = new PrincipalOAuth2User(oidcUser, profile);
        LinkedIdentity linked = new LinkedIdentity(userId, true, false);
        TokenPair tokenPair = new TokenPair("access-token", "refresh-token", "hash",
                UUID.randomUUID(), UUID.randomUUID(), Instant.now(),
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(604800));
        AuthTokenResponse mockResponse = mock(AuthTokenResponse.class);
        AuthService.AuthTokenBundle bundle = new AuthService.AuthTokenBundle(tokenPair, mockResponse);

        when(authentication.getPrincipal()).thenReturn(principal);
        when(identityLinker.link(profile)).thenReturn(linked);
        when(sessionIssuer.issue(eq(userId), any(ClientContext.class))).thenReturn(bundle);

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());

        String redirectUrl = redirectCaptor.getValue();
        assertThat(redirectUrl).startsWith("http://localhost:3000/oauth/callback?");
        assertThat(redirectUrl).doesNotContain(" ");
    }
}
