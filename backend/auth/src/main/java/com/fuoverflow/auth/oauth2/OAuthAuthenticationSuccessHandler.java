package com.fuoverflow.auth.oauth2;

import com.fuoverflow.auth.application.OAuthIdentityLinker;
import com.fuoverflow.auth.application.OAuthSessionIssuer;
import com.fuoverflow.auth.application.CookieService;
import com.fuoverflow.auth.application.AuthService;
import com.fuoverflow.auth.config.OAuth2Properties;
import com.fuoverflow.auth.domain.ClientContext;
import com.fuoverflow.auth.domain.LinkedIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuthAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuthAuthenticationSuccessHandler.class);

    private final OAuthIdentityLinker identityLinker;
    private final OAuthSessionIssuer sessionIssuer;
    private final CookieService cookieService;
    private final OAuth2Properties oauth2Properties;

    public OAuthAuthenticationSuccessHandler(OAuthIdentityLinker identityLinker, OAuthSessionIssuer sessionIssuer,
                                            CookieService cookieService, OAuth2Properties oauth2Properties) {
        this.identityLinker = identityLinker;
        this.sessionIssuer = sessionIssuer;
        this.cookieService = cookieService;
        this.oauth2Properties = oauth2Properties;
        validateRedirectUrls(oauth2Properties);
    }

    private static void validateRedirectUrls(OAuth2Properties props) {
        String base = props.frontendBaseUrl();
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("app.oauth2.frontend-base-url must be set");
        }
        if (!props.successRedirect().startsWith(base)) {
            throw new IllegalStateException(
                    "app.oauth2.success-redirect must start with frontend-base-url: " + base);
        }
        if (!props.errorRedirect().startsWith(base)) {
            throw new IllegalStateException(
                    "app.oauth2.error-redirect must start with frontend-base-url: " + base);
        }
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                       Authentication authentication) throws IOException {
        PrincipalOAuth2User principal = (PrincipalOAuth2User) authentication.getPrincipal();
        LinkedIdentity linked = identityLinker.link(principal.profile());

        ClientContext context = new ClientContext(request.getRemoteAddr(), request.getHeader("User-Agent"));
        AuthService.AuthTokenBundle bundle = sessionIssuer.issue(linked.userId(), context);

        cookieService.writeTokenCookies(response, bundle.tokenPair());

        log.info("OAUTH_LOGIN_SUCCESS userId={} provider=google new={} linked={}",
                linked.userId(), linked.isNewUser(), linked.isLinkedToExisting());

        String redirectUrl = UriComponentsBuilder.fromHttpUrl(oauth2Properties.successRedirect())
                .queryParam("provider", "google")
                .queryParam("new", linked.isNewUser())
                .build().toUriString();

        response.sendRedirect(redirectUrl);
    }
}
