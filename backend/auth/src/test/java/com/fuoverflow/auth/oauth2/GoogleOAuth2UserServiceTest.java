package com.fuoverflow.auth.oauth2;

import com.fuoverflow.auth.domain.ProviderProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoogleOAuth2UserServiceTest {

    @Mock
    private OidcUserRequest userRequest;

    @InjectMocks
    private GoogleOAuth2UserService service;

    @Test
    void shouldLoadUserWithAllFields() {
        OidcUser mockOidcUser = createMockOidcUser("123", "test@example.com", true, "Test User", "https://example.com/pic.jpg");
        GoogleOAuth2UserService spyService = new GoogleOAuth2UserService() {
            @Override
            public OidcUser loadUser(OidcUserRequest userRequest) {
                return new PrincipalOAuth2User(mockOidcUser,
                    new ProviderProfile("google", mockOidcUser.getSubject(), mockOidcUser.getEmail(),
                        mockOidcUser.getEmailVerified(), mockOidcUser.getFullName(), mockOidcUser.getPicture()));
            }
        };

        OidcUser result = spyService.loadUser(userRequest);

        assertThat(result).isInstanceOf(PrincipalOAuth2User.class);
        PrincipalOAuth2User principal = (PrincipalOAuth2User) result;
        ProviderProfile profile = principal.profile();

        assertThat(profile.provider()).isEqualTo("google");
        assertThat(profile.providerUserId()).isEqualTo("123");
        assertThat(profile.email()).isEqualTo("test@example.com");
        assertThat(profile.emailVerified()).isTrue();
        assertThat(profile.displayName()).isEqualTo("Test User");
        assertThat(profile.avatarUrl()).isEqualTo("https://example.com/pic.jpg");
    }

    @Test
    void shouldHandleNullEmail() {
        OidcUser mockOidcUser = createMockOidcUser("123", null, false, "Test User", null);
        GoogleOAuth2UserService spyService = new GoogleOAuth2UserService() {
            @Override
            public OidcUser loadUser(OidcUserRequest userRequest) {
                return new PrincipalOAuth2User(mockOidcUser,
                    new ProviderProfile("google", mockOidcUser.getSubject(), mockOidcUser.getEmail(),
                        false, mockOidcUser.getFullName(), mockOidcUser.getPicture()));
            }
        };

        OidcUser result = spyService.loadUser(userRequest);

        PrincipalOAuth2User principal = (PrincipalOAuth2User) result;
        assertThat(principal.profile().email()).isNull();
        assertThat(principal.profile().emailVerified()).isFalse();
    }

    @Test
    void shouldHandleNullName() {
        OidcUser mockOidcUser = createMockOidcUser("123", "test@example.com", true, null, null);
        GoogleOAuth2UserService spyService = new GoogleOAuth2UserService() {
            @Override
            public OidcUser loadUser(OidcUserRequest userRequest) {
                return new PrincipalOAuth2User(mockOidcUser,
                    new ProviderProfile("google", mockOidcUser.getSubject(), mockOidcUser.getEmail(),
                        mockOidcUser.getEmailVerified(), mockOidcUser.getFullName(), mockOidcUser.getPicture()));
            }
        };

        OidcUser result = spyService.loadUser(userRequest);

        PrincipalOAuth2User principal = (PrincipalOAuth2User) result;
        assertThat(principal.profile().displayName()).isNull();
    }

    @Test
    void shouldHandleEmailVerifiedFalse() {
        OidcUser mockOidcUser = createMockOidcUser("123", "test@example.com", false, "Test User", null);
        GoogleOAuth2UserService spyService = new GoogleOAuth2UserService() {
            @Override
            public OidcUser loadUser(OidcUserRequest userRequest) {
                return new PrincipalOAuth2User(mockOidcUser,
                    new ProviderProfile("google", mockOidcUser.getSubject(), mockOidcUser.getEmail(),
                        mockOidcUser.getEmailVerified() != null && mockOidcUser.getEmailVerified(),
                        mockOidcUser.getFullName(), mockOidcUser.getPicture()));
            }
        };

        OidcUser result = spyService.loadUser(userRequest);

        PrincipalOAuth2User principal = (PrincipalOAuth2User) result;
        assertThat(principal.profile().emailVerified()).isFalse();
    }

    @Test
    void shouldHandleEmailVerifiedNull() {
        OidcUser mockOidcUser = createMockOidcUser("123", "test@example.com", null, "Test User", null);
        GoogleOAuth2UserService spyService = new GoogleOAuth2UserService() {
            @Override
            public OidcUser loadUser(OidcUserRequest userRequest) {
                return new PrincipalOAuth2User(mockOidcUser,
                    new ProviderProfile("google", mockOidcUser.getSubject(), mockOidcUser.getEmail(),
                        mockOidcUser.getEmailVerified() != null && mockOidcUser.getEmailVerified(),
                        mockOidcUser.getFullName(), mockOidcUser.getPicture()));
            }
        };

        OidcUser result = spyService.loadUser(userRequest);

        PrincipalOAuth2User principal = (PrincipalOAuth2User) result;
        assertThat(principal.profile().emailVerified()).isFalse();
    }

    private OidcUser createMockOidcUser(String sub, String email, Boolean emailVerified, String name, String picture) {
        OidcUser mockUser = mock(OidcUser.class);
        when(mockUser.getSubject()).thenReturn(sub);
        when(mockUser.getEmail()).thenReturn(email);
        when(mockUser.getEmailVerified()).thenReturn(emailVerified);
        when(mockUser.getFullName()).thenReturn(name);
        when(mockUser.getPicture()).thenReturn(picture);
        return mockUser;
    }
}
