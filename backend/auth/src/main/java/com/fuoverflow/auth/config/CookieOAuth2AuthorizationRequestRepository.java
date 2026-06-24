package com.fuoverflow.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Cookie-based {@link AuthorizationRequestRepository} for OAuth2 authorization requests.
 * <p>
 * Serializes the {@link OAuth2AuthorizationRequest} to a Base64-encoded cookie so that
 * multi-instance deployments work without sticky sessions: the callback request can hit
 * any instance and still retrieve the authorization request (including the {@code state}
 * parameter) from the cookie.
 * <p>
 * Cookie attributes mirror the auth cookie settings (HttpOnly, Secure, SameSite=Lax,
 * Path=/) with a short Max-Age of 300 s — enough time for the OAuth2 round-trip.
 */
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE_NAME = "oauth2_auth_request";
    private static final int MAX_AGE_SECONDS = 300;

    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;

    public CookieOAuth2AuthorizationRequestRepository(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return readCookie(request);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        if (authorizationRequest == null) {
            deleteCookie(request, response);
            return;
        }
        String value = serialize(authorizationRequest);
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(authProperties.cookie().secure())
                .sameSite(authProperties.cookie().sameSite())
                .path("/")
                .maxAge(MAX_AGE_SECONDS)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                  HttpServletResponse response) {
        OAuth2AuthorizationRequest existing = readCookie(request);
        if (existing != null) {
            deleteCookie(request, response);
        }
        return existing;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private OAuth2AuthorizationRequest readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c -> COOKIE_NAME.equals(c.getName()))
                .findFirst()
                .map(c -> deserialize(c.getValue()))
                .orElse(null);
    }

    private void deleteCookie(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return;
        }
        boolean present = Arrays.stream(cookies).anyMatch(c -> COOKIE_NAME.equals(c.getName()));
        if (!present) {
            return;
        }
        ResponseCookie expired = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(authProperties.cookie().secure())
                .sameSite(authProperties.cookie().sameSite())
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", expired.toString());
    }

    private String serialize(OAuth2AuthorizationRequest request) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("authorizationUri", request.getAuthorizationUri());
            data.put("clientId", request.getClientId());
            data.put("redirectUri", request.getRedirectUri());
            data.put("scopes", request.getScopes());
            data.put("state", request.getState());
            data.put("authorizationRequestUri", request.getAuthorizationRequestUri());
            data.put("grantType", request.getGrantType().getValue());
            if (request.getAdditionalParameters() != null && !request.getAdditionalParameters().isEmpty()) {
                data.put("additionalParameters", request.getAdditionalParameters());
            }
            if (request.getAttributes() != null && !request.getAttributes().isEmpty()) {
                data.put("attributes", request.getAttributes());
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    objectMapper.writeValueAsBytes(data));
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private OAuth2AuthorizationRequest deserialize(String encoded) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(encoded);
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest
                    .authorizationCode()
                    .authorizationUri((String) data.get("authorizationUri"))
                    .clientId((String) data.get("clientId"))
                    .redirectUri((String) data.get("redirectUri"))
                    .state((String) data.get("state"))
                    .authorizationRequestUri((String) data.get("authorizationRequestUri"));
            Object scopes = data.get("scopes");
            if (scopes instanceof Collection<?> scopeList) {
                Set<String> scopeSet = new LinkedHashSet<>();
                scopeList.forEach(sc -> scopeSet.add(sc.toString()));
                builder.scopes(scopeSet);
            }
            Object additionalParams = data.get("additionalParameters");
            if (additionalParams instanceof Map<?, ?> params) {
                builder.additionalParameters(m -> params.forEach((k, v) -> m.put(k.toString(), v)));
            }
            Object attrs = data.get("attributes");
            if (attrs instanceof Map<?, ?> attrMap) {
                builder.attributes(a -> attrMap.forEach((k, v) -> a.put(k.toString(), v)));
            }
            return builder.build();
        } catch (Exception e) {
            // Corrupted or expired cookie — treat as absent; Spring Security will restart the flow
            return null;
        }
    }
}
