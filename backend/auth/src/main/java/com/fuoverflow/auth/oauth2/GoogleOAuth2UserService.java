package com.fuoverflow.auth.oauth2;

import com.fuoverflow.auth.domain.ProviderProfile;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class GoogleOAuth2UserService extends OidcUserService {
    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String sub = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        Boolean emailVerified = oidcUser.getEmailVerified();
        String name = oidcUser.getFullName();
        String picture = oidcUser.getPicture();

        ProviderProfile profile = new ProviderProfile(
                "google", sub, email, emailVerified != null && emailVerified, name, picture);

        return new PrincipalOAuth2User(oidcUser, profile);
    }
}
