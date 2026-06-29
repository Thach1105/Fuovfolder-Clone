package com.fuoverflow.auth.application;

import com.fuoverflow.auth.domain.LinkedIdentity;
import com.fuoverflow.auth.domain.ProviderProfile;
import com.fuoverflow.auth.exception.OAuthEmailNotVerifiedException;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.application.UserLookupService;
import com.fuoverflow.user.application.UserRegistrationService;
import com.fuoverflow.user.domain.UserStatus;
import com.fuoverflow.user.persistence.UserOAuthAccountEntity;
import com.fuoverflow.user.persistence.UserOAuthAccountRepository;
import com.fuoverflow.user.validation.EmailNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthIdentityLinkerTest {
    @Mock private UserOAuthAccountRepository oauthAccounts;
    @Mock private UserLookupService users;
    @Mock private UserRegistrationService registrations;
    @Mock private EmailNormalizer emailNormalizer;

    private OAuthIdentityLinker linker;

    @BeforeEach
    void setUp() {
        linker = new OAuthIdentityLinker(oauthAccounts, users, registrations, emailNormalizer);
    }

    @Test
    void link_existingOAuthAccount_returnsUserId() {
        UUID userId = UUID.randomUUID();
        UserOAuthAccountEntity existing = UserOAuthAccountEntity.create(
                UUID.randomUUID(), userId, "google", "google-sub-123", "user@example.com",
                "User Name", "https://avatar.url", Instant.now());
        when(oauthAccounts.findByProviderAndProviderUserId("google", "google-sub-123"))
                .thenReturn(Optional.of(existing));
        AuthUserView activeUser = new AuthUserView(userId, "user@example.com", "username", null,
                "User Name", UserStatus.ACTIVE, List.of(), 0, List.of(), false, true, null, null, null);
        when(users.findAuthUserById(userId)).thenReturn(Optional.of(activeUser));

        ProviderProfile profile = new ProviderProfile("google", "google-sub-123",
                "user@example.com", true, "User Name", "https://avatar.url");
        LinkedIdentity result = linker.link(profile);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.isNewUser()).isFalse();
        assertThat(result.isLinkedToExisting()).isFalse();
        verify(oauthAccounts, never()).save(any());
    }

    @Test
    void link_verifiedEmailMatch_linksExistingUser() {
        UUID userId = UUID.randomUUID();
        when(oauthAccounts.findByProviderAndProviderUserId("google", "google-sub-456"))
                .thenReturn(Optional.empty());
        when(emailNormalizer.normalize("user@example.com")).thenReturn("user@example.com");

        AuthUserView user = new AuthUserView(userId, "user@example.com", "existing_user",
                "hash", "Existing User", UserStatus.ACTIVE, List.of("USER"), 0L, List.of(),
                false, true, Instant.now(), null, null);
        when(users.findAuthUserByIdentifier("user@example.com")).thenReturn(Optional.of(user));

        ProviderProfile profile = new ProviderProfile("google", "google-sub-456",
                "user@example.com", true, "User Name", "https://avatar.url");
        LinkedIdentity result = linker.link(profile);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.isNewUser()).isFalse();
        assertThat(result.isLinkedToExisting()).isTrue();
        verify(oauthAccounts).save(any(UserOAuthAccountEntity.class));
    }

    @Test
    void link_newUser_createsUserAndOAuthAccount() {
        when(oauthAccounts.findByProviderAndProviderUserId("google", "google-sub-789"))
                .thenReturn(Optional.empty());
        when(emailNormalizer.normalize("newuser@example.com")).thenReturn("newuser@example.com");
        when(users.findAuthUserByIdentifier("newuser@example.com")).thenReturn(Optional.empty());

        UUID newUserId = UUID.randomUUID();
        AuthUserView newUser = new AuthUserView(newUserId, "newuser@example.com", "newuser_abc123",
                null, "New User", UserStatus.PENDING_PROFILE, List.of("USER"), 0L, List.of(),
                false, true, null, null, null);
        when(registrations.register(any())).thenReturn(newUser);

        ProviderProfile profile = new ProviderProfile("google", "google-sub-789",
                "newuser@example.com", true, "New User", null);
        LinkedIdentity result = linker.link(profile);

        assertThat(result.userId()).isEqualTo(newUserId);
        assertThat(result.isNewUser()).isTrue();
        assertThat(result.isLinkedToExisting()).isFalse();
        verify(registrations).register(any());
        verify(oauthAccounts).save(any(UserOAuthAccountEntity.class));
    }

    @Test
    void link_emailNotVerified_throwsException() {
        ProviderProfile profile = new ProviderProfile("google", "google-sub-999",
                "unverified@example.com", false, "Unverified", null);

        assertThatThrownBy(() -> linker.link(profile))
                .isInstanceOf(OAuthEmailNotVerifiedException.class);
    }

    @Test
    void link_newUser_malformedEmail_usesFallbackPrefix() {
        when(oauthAccounts.findByProviderAndProviderUserId("google", "google-sub-malformed"))
                .thenReturn(Optional.empty());
        when(emailNormalizer.normalize("malformed-email-no-at-sign")).thenReturn("malformed-email-no-at-sign");
        when(users.findAuthUserByIdentifier("malformed-email-no-at-sign")).thenReturn(Optional.empty());

        UUID newUserId = UUID.randomUUID();
        AuthUserView newUser = new AuthUserView(newUserId, "malformed-email-no-at-sign", "user_abc123",
                null, "New User", UserStatus.PENDING_PROFILE, List.of("USER"), 0L, List.of(),
                false, true, null, null, null);
        when(registrations.register(any())).thenReturn(newUser);

        // Email without '@' should not throw - should use fallback "user" prefix
        ProviderProfile profile = new ProviderProfile("google", "google-sub-malformed",
                "malformed-email-no-at-sign", true, null, null);
        LinkedIdentity result = linker.link(profile);

        assertThat(result.userId()).isEqualTo(newUserId);
        assertThat(result.isNewUser()).isTrue();
        assertThat(result.isLinkedToExisting()).isFalse();
        verify(registrations).register(any());
        verify(oauthAccounts).save(any(UserOAuthAccountEntity.class));
    }
}
