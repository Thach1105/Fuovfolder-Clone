package com.fuoverflow.auth.oauth2;

import com.fuoverflow.auth.config.OAuth2Properties;
import com.fuoverflow.auth.exception.OAuthEmailNotVerifiedException;
import com.fuoverflow.common.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthAuthenticationFailureHandlerTest {

    @Mock private OAuth2Properties oauth2Properties;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private OAuthAuthenticationFailureHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuthAuthenticationFailureHandler(oauth2Properties);
        when(oauth2Properties.errorRedirect()).thenReturn("http://localhost:3000/oauth/error");
    }

    @Test
    void shouldMapOAuthEmailNotVerifiedException() throws Exception {
        AuthenticationException exception = new AuthenticationException("Failed", new OAuthEmailNotVerifiedException("Email not verified")) {};

        handler.onAuthenticationFailure(request, response, exception);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());

        String redirectUrl = redirectCaptor.getValue();
        assertThat(redirectUrl).contains("http://localhost:3000/oauth/error");
        assertThat(redirectUrl).contains("code=OAUTH_EMAIL_NOT_VERIFIED");
    }

    @Test
    void shouldMapForbiddenException() throws Exception {
        AuthenticationException exception = new AuthenticationException("Failed", new ForbiddenException("USER_BLOCKED", "User blocked")) {};

        handler.onAuthenticationFailure(request, response, exception);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());

        String redirectUrl = redirectCaptor.getValue();
        assertThat(redirectUrl).contains("code=OAUTH_USER_BLOCKED");
    }

    @Test
    void shouldMapAccessDenied() throws Exception {
        OAuth2Error error = new OAuth2Error("access_denied", "User denied access", null);
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(error);

        handler.onAuthenticationFailure(request, response, exception);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());

        String redirectUrl = redirectCaptor.getValue();
        assertThat(redirectUrl).contains("code=OAUTH_CANCELED");
    }

    @Test
    void shouldMapGenericError() throws Exception {
        AuthenticationException exception = new AuthenticationException("Generic failure") {};

        handler.onAuthenticationFailure(request, response, exception);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());

        String redirectUrl = redirectCaptor.getValue();
        assertThat(redirectUrl).contains("code=OAUTH_PROVIDER_ERROR");
    }

    @Test
    void shouldConstructRedirectUrlWithUriComponentsBuilder() throws Exception {
        AuthenticationException exception = new AuthenticationException("Failed") {};

        handler.onAuthenticationFailure(request, response, exception);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());

        String redirectUrl = redirectCaptor.getValue();
        assertThat(redirectUrl).startsWith("http://localhost:3000/oauth/error?");
        assertThat(redirectUrl).doesNotContain(" ");
        assertThat(redirectUrl).matches(".*\\?code=[A-Z_]+");
    }

    @Test
    void shouldHandleSpecialCharactersInErrorCode() throws Exception {
        when(oauth2Properties.errorRedirect()).thenReturn("http://localhost:3000/oauth/error");
        AuthenticationException exception = new AuthenticationException("Failed") {};

        handler.onAuthenticationFailure(request, response, exception);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());

        String redirectUrl = redirectCaptor.getValue();
        assertThat(redirectUrl).doesNotContain("&");
        assertThat(redirectUrl).contains("?code=");
    }
}
