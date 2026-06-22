package com.fuoverflow.auth.oauth2;

import com.fuoverflow.auth.domain.ProviderProfile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.Map;

public class PrincipalOAuth2User implements OidcUser {
    private final OidcUser delegate;
    private final ProviderProfile profile;

    public PrincipalOAuth2User(OidcUser delegate, ProviderProfile profile) {
        this.delegate = delegate;
        this.profile = profile;
    }

    public ProviderProfile profile() {
        return profile;
    }

    @Override
    public Map<String, Object> getClaims() { return delegate.getClaims(); }

    @Override
    public OidcUserInfo getUserInfo() { return delegate.getUserInfo(); }

    @Override
    public OidcIdToken getIdToken() { return delegate.getIdToken(); }

    @Override
    public Map<String, Object> getAttributes() { return delegate.getAttributes(); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return delegate.getAuthorities(); }

    @Override
    public String getName() { return delegate.getName(); }
}
