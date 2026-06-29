package com.fuoverflow.auth.application;

import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.user.application.UserLookupService;
import com.fuoverflow.user.application.UserPasswordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ChangePasswordService {
    private final UserLookupService users;
    private final UserPasswordService userPasswords;
    private final UserSessionRepository sessions;
    private final PasswordService passwords;

    public ChangePasswordService(UserLookupService users,
                                  UserPasswordService userPasswords,
                                  UserSessionRepository sessions,
                                  PasswordService passwords) {
        this.users = users;
        this.userPasswords = userPasswords;
        this.sessions = sessions;
        this.passwords = passwords;
    }

    @Transactional
    public void changePassword(UUID userId, UUID currentSessionId, String currentPassword, String newPassword) {
        var user = users.findAuthUserById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        if (user.passwordHash() == null) {
            throw new BadRequestException("NO_PASSWORD_TO_CHANGE",
                    "Tài khoản này chưa có mật khẩu. Hãy sử dụng tính năng đặt mật khẩu.");
        }
        if (!passwords.matches(currentPassword, user.passwordHash())) {
            throw new BadRequestException("WRONG_PASSWORD", "Mật khẩu hiện tại không đúng.");
        }
        passwords.validatePolicy(newPassword);
        Instant now = Instant.now();
        userPasswords.updatePassword(userId, passwords.encode(newPassword), now);
        sessions.revokeAllExcept(userId, currentSessionId, "CHANGE_PASSWORD", now);
    }
}
