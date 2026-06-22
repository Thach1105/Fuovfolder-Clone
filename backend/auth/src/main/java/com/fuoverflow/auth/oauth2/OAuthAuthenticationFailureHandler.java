package com.fuoverflow.auth.oauth2;

import com.fuoverflow.auth.config.OAuth2Properties;
import com.fuoverflow.auth.exception.OAuthEmailNotVerifiedException;
import com.fuoverflow.common.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuthAuthenticationFailureHandler implements AuthenticationFailureHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuthAuthenticationFailureHandler.class);

    private final OAuth2Properties oauth2Properties;

    public OAuthAuthenticationFailureHandler(OAuth2Properties oauth2Properties) {
        this.oauth2Properties = oauth2Properties;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                       AuthenticationException exception) throws IOException {
        String code = determineErrorCode(exception);
        log.warn("OAUTH_LOGIN_FAILURE code={} provider=google", code);

        String redirectUrl = oauth2Properties.errorRedirect() + "?code=" + code;
        response.sendRedirect(redirectUrl);
    }

    private String determineErrorCode(AuthenticationException exception) {
        if (exception.getCause() instanceof OAuthEmailNotVerifiedException) {
            return "OAUTH_EMAIL_NOT_VERIFIED";
        }
        if (exception.getCause() instanceof ForbiddenException) {
            return "OAUTH_USER_BLOCKED";
        }
        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            if (oauth2Exception.getError().getErrorCode().contains("access_denied")) {
                return "OAUTH_CANCELED";
            }
        }
        return "OAUTH_PROVIDER_ERROR";
    }
}
