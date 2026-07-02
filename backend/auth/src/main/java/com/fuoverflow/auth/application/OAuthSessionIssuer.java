package com.fuoverflow.auth.application;

import com.fuoverflow.auth.api.dto.AuthTokenResponse;
import com.fuoverflow.auth.api.dto.AuthenticatedUserResponse;
import com.fuoverflow.auth.domain.ClientContext;
import com.fuoverflow.auth.domain.TokenPair;
import com.fuoverflow.auth.persistence.UserSessionEntity;
import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.exception.UnauthorizedException;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.application.UserLookupService;
import com.fuoverflow.user.domain.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OAuthSessionIssuer {
    private final UserLookupService users;
    private final JwtService jwt;
    private final UserSessionRepository sessions;
    private final CookieService cookies;

    public OAuthSessionIssuer(UserLookupService users, JwtService jwt,
                              UserSessionRepository sessions, CookieService cookies) {
        this.users = users;
        this.jwt = jwt;
        this.sessions = sessions;
        this.cookies = cookies;
    }

    @Transactional
    public AuthService.AuthTokenBundle issue(UUID userId, ClientContext context) {
        AuthUserView user = users.findAuthUserById(userId)
                .orElseThrow(() -> new UnauthorizedException("USER_NOT_FOUND", "User not found"));

        if (!user.status().canAuthenticate()) {
            throw new ForbiddenException("USER_DISABLED", "User cannot authenticate");
        }

        Instant now = Instant.now();
        UUID sessionId = UUID.randomUUID();
        TokenPair pair = jwt.generate(user, sessionId, now);

        sessions.save(UserSessionEntity.create(sessionId, user.id(), pair.refreshTokenHash(), sessionId,
                pair.refreshTokenJti(), pair.accessTokenJti(), pair.issuedAt(), pair.accessExpiresAt(),
                pair.refreshExpiresAt(), context.ipAddress(), context.userAgent()));

        return new AuthService.AuthTokenBundle(pair,
                new AuthTokenResponse("Bearer", pair.accessExpiresAt(),
                        pair.refreshExpiresAt(), pair.issuedAt(),
                        new AuthenticatedUserResponse(user.id(), user.email(),
                                user.username(), user.displayName(), user.status(), user.emailVerified())));
    }
}
