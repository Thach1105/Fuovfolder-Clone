package com.fuoverflow.auth.application;

import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.UnauthorizedException;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.application.UserLookupService;
import com.fuoverflow.user.application.UserPasswordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserPasswordManagementService {
    private final PasswordService passwordService;
    private final UserPasswordService userPasswordService;
    private final UserSessionRepository sessionRepository;
    private final UserLookupService userLookupService;

    public UserPasswordManagementService(PasswordService passwordService,
                                        UserPasswordService userPasswordService,
                                        UserSessionRepository sessionRepository,
                                        UserLookupService userLookupService) {
        this.passwordService = passwordService;
        this.userPasswordService = userPasswordService;
        this.sessionRepository = sessionRepository;
        this.userLookupService = userLookupService;
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword, UUID currentSessionId) {
        // Load user
        AuthUserView user = userLookupService.findAuthUserById(userId)
                .orElseThrow(() -> new UnauthorizedException("USER_NOT_FOUND", "User not found"));

        // Verify current password matches
        if (!passwordService.matches(currentPassword, user.passwordHash())) {
            throw new BadRequestException("INVALID_PASSWORD", "Current password is incorrect");
        }

        // Validate new password policy
        passwordService.validatePolicy(newPassword);

        // Check new password != current password
        if (passwordService.matches(newPassword, user.passwordHash())) {
            throw new BadRequestException("SAME_PASSWORD", "New password must be different from current password");
        }

        // Hash new password
        String newHash = passwordService.encode(newPassword);

        // Update password
        userPasswordService.updatePassword(userId, newHash);

        // Revoke other sessions (keep current one)
        sessionRepository.revokeAllExceptSession(userId, currentSessionId, "PASSWORD_CHANGED", Instant.now());
    }
}
