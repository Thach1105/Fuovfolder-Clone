package com.fuoverflow.auth.application;

import com.fuoverflow.auth.domain.LinkedIdentity;
import com.fuoverflow.auth.domain.ProviderProfile;
import com.fuoverflow.auth.exception.OAuthEmailNotVerifiedException;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.application.RegisterUserCommand;
import com.fuoverflow.user.application.UserLookupService;
import com.fuoverflow.user.application.UserRegistrationService;
import com.fuoverflow.user.domain.UserStatus;
import com.fuoverflow.user.persistence.UserOAuthAccountEntity;
import com.fuoverflow.user.persistence.UserOAuthAccountRepository;
import com.fuoverflow.user.validation.EmailNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OAuthIdentityLinker {
    private static final int USERNAME_BASE_MAX_LENGTH = 60;
    private static final int USERNAME_SUFFIX_LENGTH = 6;
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();
    // Display name truncation limit to fit database column constraint
    private static final int DISPLAY_NAME_MAX_LENGTH = 120;

    private final UserOAuthAccountRepository oauthAccounts;
    private final UserLookupService users;
    private final UserRegistrationService registrations;
    private final EmailNormalizer emailNormalizer;

    public OAuthIdentityLinker(UserOAuthAccountRepository oauthAccounts, UserLookupService users,
                               UserRegistrationService registrations, EmailNormalizer emailNormalizer) {
        this.oauthAccounts = oauthAccounts;
        this.users = users;
        this.registrations = registrations;
        this.emailNormalizer = emailNormalizer;
    }

    @Transactional
    public LinkedIdentity link(ProviderProfile profile) {
        if (!profile.emailVerified()) {
            throw new OAuthEmailNotVerifiedException();
        }

        // Case A: existing OAuth account
        var existing = oauthAccounts.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId());
        if (existing.isPresent()) {
            return new LinkedIdentity(existing.get().getUserId(), false, false);
        }

        // Case B: verified email matches existing user.
        // SECURITY: Auto-linking by verified email is safe for Google (Google verifies email ownership).
        // If adding providers that don't strictly verify email (GitHub private email, Facebook),
        // require explicit user confirmation before linking to prevent account takeover.
        String normalizedEmail = emailNormalizer.normalize(profile.email());
        var userByEmail = users.findAuthUserByIdentifier(normalizedEmail);
        if (userByEmail.isPresent()) {
            UUID userId = userByEmail.get().id();
            saveOAuthAccount(userId, profile);
            return new LinkedIdentity(userId, false, true);
        }

        // Case C: new user
        String username = generateUsername(profile.email());
        String trimmed = profile.displayName() != null ? profile.displayName().trim() : "";
        String displayName = !trimmed.isBlank()
                ? trimmed.substring(0, Math.min(DISPLAY_NAME_MAX_LENGTH, trimmed.length()))
                : (profile.email().contains("@") ? profile.email().split("@")[0] : "User");

        RegisterUserCommand command = new RegisterUserCommand(
                profile.email(), username, null, displayName, null, true, UserStatus.PENDING_PROFILE);
        AuthUserView newUser = registrations.register(command);
        saveOAuthAccount(newUser.id(), profile);
        return new LinkedIdentity(newUser.id(), true, false);
    }

    private void saveOAuthAccount(UUID userId, ProviderProfile profile) {
        Instant now = Instant.now();
        UserOAuthAccountEntity entity = UserOAuthAccountEntity.create(
                UUID.randomUUID(), userId, profile.provider(), profile.providerUserId(),
                profile.email(), profile.displayName(), profile.avatarUrl(), now);
        oauthAccounts.save(entity);
    }

    private String generateUsername(String email) {
        // Defensive: handle malformed email without '@' symbol
        String localPart = email.contains("@") ? email.split("@")[0] : "user";
        String slugified = localPart.toLowerCase().replaceAll("[^a-z0-9_-]", "");

        // Guard: if email local part contained only special chars, use fallback
        if (slugified.isEmpty()) {
            slugified = "user";
        }

        if (slugified.length() > USERNAME_BASE_MAX_LENGTH) {
            slugified = slugified.substring(0, USERNAME_BASE_MAX_LENGTH);
        }

        byte[] bytes = new byte[4];
        SECURE_RANDOM.nextBytes(bytes);
        String suffix = Integer.toUnsignedString(
                ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) |
                ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF), 36);
        return slugified + "_" + suffix;
    }
}
