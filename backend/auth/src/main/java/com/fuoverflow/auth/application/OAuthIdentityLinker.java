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
            throw new OAuthEmailNotVerifiedException("OAuth provider email_verified is false");
        }

        // Case A: existing OAuth account
        var existing = oauthAccounts.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId());
        if (existing.isPresent()) {
            return new LinkedIdentity(existing.get().getUserId(), false, false);
        }

        // Case B: verified email matches existing user
        String normalizedEmail = emailNormalizer.normalize(profile.email());
        var userByEmail = users.findAuthUserByIdentifier(normalizedEmail);
        if (userByEmail.isPresent()) {
            UUID userId = userByEmail.get().id();
            saveOAuthAccount(userId, profile);
            return new LinkedIdentity(userId, false, true);
        }

        // Case C: new user
        String username = generateUsername(profile.email());
        String displayName = profile.displayName() != null && !profile.displayName().isBlank()
                ? profile.displayName().trim().substring(0, Math.min(120, profile.displayName().trim().length()))
                : profile.email().split("@")[0];

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
        String localPart = email.split("@")[0];
        String slugified = localPart.toLowerCase().replaceAll("[^a-z0-9_-]", "");
        if (slugified.length() > 60) {
            slugified = slugified.substring(0, 60);
        }
        String base36 = Long.toString(System.nanoTime() % 2176782336L, 36);
        return slugified + "_" + base36;
    }
}
