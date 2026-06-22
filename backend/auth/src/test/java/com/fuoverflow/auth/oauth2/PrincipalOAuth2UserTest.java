package com.fuoverflow.auth.oauth2;

import com.fuoverflow.auth.domain.ProviderProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrincipalOAuth2UserTest {

    @Mock
    private OidcUser mockDelegate;

    @Mock
    private OidcIdToken mockIdToken;

    @Mock
    private OidcUserInfo mockUserInfo;

    @Test
    void shouldDelegateGetClaims() {
        Map<String, Object> claims = Map.of("sub", "123", "email", "test@example.com");
        when(mockDelegate.getClaims()).thenReturn(claims);

        ProviderProfile profile = new ProviderProfile("google", "123", "test@example.com", true, "Test User", null);
        PrincipalOAuth2User principal = new PrincipalOAuth2User(mockDelegate, profile);

        assertThat(principal.getClaims()).isEqualTo(claims);
    }

    @Test
    void shouldDelegateGetUserInfo() {
        when(mockDelegate.getUserInfo()).thenReturn(mockUserInfo);

        ProviderProfile profile = new ProviderProfile("google", "123", "test@example.com", true, "Test User", null);
        PrincipalOAuth2User principal = new PrincipalOAuth2User(mockDelegate, profile);

        assertThat(principal.getUserInfo()).isEqualTo(mockUserInfo);
    }

    @Test
    void shouldDelegateGetIdToken() {
        when(mockDelegate.getIdToken()).thenReturn(mockIdToken);

        ProviderProfile profile = new ProviderProfile("google", "123", "test@example.com", true, "Test User", null);
        PrincipalOAuth2User principal = new PrincipalOAuth2User(mockDelegate, profile);

        assertThat(principal.getIdToken()).isEqualTo(mockIdToken);
    }

    @Test
    void shouldDelegateGetAttributes() {
        Map<String, Object> attributes = Map.of("name", "Test User");
        when(mockDelegate.getAttributes()).thenReturn(attributes);

        ProviderProfile profile = new ProviderProfile("google", "123", "test@example.com", true, "Test User", null);
        PrincipalOAuth2User principal = new PrincipalOAuth2User(mockDelegate, profile);

        assertThat(principal.getAttributes()).isEqualTo(attributes);
    }

    @Test
    void shouldDelegateGetName() {
        when(mockDelegate.getName()).thenReturn("Test User");

        ProviderProfile profile = new ProviderProfile("google", "123", "test@example.com", true, "Test User", null);
        PrincipalOAuth2User principal = new PrincipalOAuth2User(mockDelegate, profile);

        assertThat(principal.getName()).isEqualTo("Test User");
    }

    @Test
    void shouldReturnProfile() {
        ProviderProfile profile = new ProviderProfile("google", "123", "test@example.com", true, "Test User", "https://example.com/pic.jpg");
        PrincipalOAuth2User principal = new PrincipalOAuth2User(mockDelegate, profile);

        assertThat(principal.profile()).isEqualTo(profile);
        assertThat(principal.profile().provider()).isEqualTo("google");
        assertThat(principal.profile().providerUserId()).isEqualTo("123");
        assertThat(principal.profile().email()).isEqualTo("test@example.com");
        assertThat(principal.profile().emailVerified()).isTrue();
        assertThat(principal.profile().displayName()).isEqualTo("Test User");
        assertThat(principal.profile().avatarUrl()).isEqualTo("https://example.com/pic.jpg");
    }
}
