package com.fuoverflow.auth.application;

import com.fuoverflow.auth.api.dto.*;
import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.domain.ClientContext;
import com.fuoverflow.auth.domain.TokenPair;
import com.fuoverflow.auth.persistence.UserSessionEntity;
import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.exception.UnauthorizedException;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.application.RegisterUserCommand;
import com.fuoverflow.user.application.UserLookupService;
import com.fuoverflow.user.application.UserRegistrationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRegistrationService registrations;
    private final UserLookupService users;
    private final PasswordService passwords;
    private final JwtService jwt;
    private final UserSessionRepository sessions;
    private final EmailVerificationService emailVerification;
    private final VerificationEmailSender verificationEmailSender;
    private final AuthProperties authProperties;
    private final DeviceLimitEnforcer deviceLimitEnforcer;

    public AuthService(UserRegistrationService registrations, UserLookupService users, PasswordService passwords,
                       JwtService jwt, UserSessionRepository sessions, EmailVerificationService emailVerification,
                       VerificationEmailSender verificationEmailSender, AuthProperties authProperties,
                       DeviceLimitEnforcer deviceLimitEnforcer) {
        this.registrations = registrations;
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
        this.sessions = sessions;
        this.emailVerification = emailVerification;
        this.verificationEmailSender = verificationEmailSender;
        this.authProperties = authProperties;
        this.deviceLimitEnforcer = deviceLimitEnforcer;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String passwordHash = passwords.encode(request.password());
        AuthUserView user = registrations.register(new RegisterUserCommand(
                request.email(), request.username(), passwordHash, request.displayName(), request.campus()));
        String verificationToken = emailVerification.create(user.id());
        verificationEmailSender.send(user.email(), user.displayName(), verificationToken);
        return new RegisterResponse(user.id(), user.email(), user.username(), user.displayName(),
                user.status(), user.emailVerified());
    }

    @Transactional
    public AuthTokenBundle login(LoginRequest request, ClientContext context) {
        AuthUserView user = users.findAuthUserByIdentifier(request.identifier())
                .orElseThrow(() -> invalidCredentials());
        if (!passwords.matches(request.password(), user.passwordHash())) {
            throw invalidCredentials();
        }
        if (!user.status().canAuthenticate()) {
            throw new ForbiddenException("USER_DISABLED", "User cannot authenticate");
        }
        if (!user.emailVerified()) {
            throw new ForbiddenException("EMAIL_NOT_VERIFIED", "Email verification is required before login");
        }
        return createSession(user, context);
    }

    @Transactional
    public AuthTokenBundle refresh(String rawRefreshToken, ClientContext context) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("TOKEN_INVALID", "Refresh token is invalid");
        }
        Instant now = Instant.now();
        UserSessionEntity oldSession = sessions.findByRefreshTokenHash(jwt.hashRefresh(rawRefreshToken))
                .orElseThrow(() -> new UnauthorizedException("TOKEN_INVALID", "Refresh token is invalid"));
        if (oldSession.getRevokedAt() != null) {
            if ("ROTATED".equals(oldSession.getRevokedReason()) && oldSession.getRevokedAt().plusSeconds(10).isAfter(now)) {
                throw new UnauthorizedException("TOKEN_INVALID", "Refresh token was already rotated");
            }
            sessions.revokeFamily(oldSession.getRefreshTokenFamilyId(), "REFRESH_REUSE_DETECTED", now);
            throw new UnauthorizedException("REFRESH_REUSE_DETECTED", "Refresh token reuse detected");
        }
        if (!oldSession.activeAt(now)) {
            throw new UnauthorizedException("TOKEN_EXPIRED", "Refresh token is expired");
        }
        AuthUserView user = users.findAuthUserById(oldSession.getUserId())
                .orElseThrow(() -> new UnauthorizedException("SESSION_REVOKED", "Session is no longer valid"));
        if (!user.status().canAuthenticate() || !user.emailVerified()) {
            throw new UnauthorizedException("SESSION_REVOKED", "Session is no longer valid");
        }
        UUID newSessionId = UUID.randomUUID();
        TokenPair pair = jwt.generate(user, newSessionId, now);
        UserSessionEntity replacement = UserSessionEntity.create(newSessionId, user.id(), pair.refreshTokenHash(),
                oldSession.getRefreshTokenFamilyId(), pair.refreshTokenJti(), pair.accessTokenJti(), pair.issuedAt(),
                pair.accessExpiresAt(), pair.refreshExpiresAt(), context.ipAddress(), context.userAgent());
        sessions.save(replacement);
        oldSession.replaceWith(replacement.getId(), "ROTATED", now);
        return bundle(pair, user);
    }

    @Transactional
    public AuthTokenBundle generateForUser(UUID userId, ClientContext context) {
        AuthUserView user = users.findAuthUserById(userId)
                .orElseThrow(() -> new UnauthorizedException("USER_NOT_FOUND", "User not found"));
        if (!user.status().canAuthenticate() || !user.emailVerified()) {
            throw new ForbiddenException("USER_NOT_ALLOWED", "User cannot authenticate");
        }
        return createSession(user, context);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        sessions.findByRefreshTokenHash(jwt.hashRefresh(rawRefreshToken))
                .ifPresent(session -> session.revoke("LOGOUT", Instant.now()));
    }

    @Transactional(readOnly = true)
    public TokenIntrospectionResponse introspect(TokenIntrospectionRequest request) {
        if (request.tokenType() == TokenIntrospectionRequest.TokenType.ACCESS) {
            return introspectAccess(request.token());
        }
        return introspectRefresh(request.token());
    }

    public AuthenticatedUserResponse userResponse(AuthUserView user) {
        return new AuthenticatedUserResponse(user.id(), user.email(), user.username(),
                user.displayName(), user.status(), user.emailVerified());
    }

    private AuthTokenBundle createSession(AuthUserView user, ClientContext context) {
        Instant now = Instant.now();
        deviceLimitEnforcer.enforce(user.id(), now);
        UUID sessionId = UUID.randomUUID();
        TokenPair pair = jwt.generate(user, sessionId, now);
        sessions.save(UserSessionEntity.create(sessionId, user.id(), pair.refreshTokenHash(), sessionId,
                pair.refreshTokenJti(), pair.accessTokenJti(), pair.issuedAt(), pair.accessExpiresAt(),
                pair.refreshExpiresAt(), context.ipAddress(), context.userAgent()));
        return bundle(pair, user);
    }

    private AuthTokenBundle bundle(TokenPair pair, AuthUserView user) {
        AuthTokenResponse response = new AuthTokenResponse("Bearer", pair.accessExpiresAt(), pair.refreshExpiresAt(),
                pair.issuedAt(), userResponse(user));
        return new AuthTokenBundle(pair, response);
    }

    private TokenIntrospectionResponse introspectAccess(String token) {
        try {
            var jwtToken = jwt.decode(token);
            return new TokenIntrospectionResponse(true, "ACCESS", jwtToken.getSubject(),
                    jwtToken.getClaimAsString("preferred_username"), jwtToken.getClaimAsString("sid"), jwtToken.getId(),
                    jwtToken.getIssuedAt(), jwtToken.getNotBefore(), jwtToken.getExpiresAt(), null, null,
                    jwtToken.getClaimAsStringList("roles"), null);
        } catch (Exception exception) {
            return new TokenIntrospectionResponse(false, "ACCESS", null, null, null, null,
                    null, null, null, null, null, null, "TOKEN_INVALID");
        }
    }

    private TokenIntrospectionResponse introspectRefresh(String token) {
        UserSessionEntity session = sessions.findByRefreshTokenHash(jwt.hashRefresh(token)).orElse(null);
        if (session == null) {
            return new TokenIntrospectionResponse(false, "REFRESH", null, null, null, null,
                    null, null, null, null, null, null, "TOKEN_INVALID");
        }
        boolean active = session.activeAt(Instant.now());
        return new TokenIntrospectionResponse(active, "REFRESH", session.getUserId().toString(), null,
                session.getId().toString(), session.getRefreshTokenJti().toString(), session.getIssuedAt(), null,
                session.getRefreshExpiresAt(), session.getRevokedAt(), session.getRevokedReason(), null,
                active ? null : "TOKEN_INVALID");
    }

    private UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("INVALID_CREDENTIALS", "Invalid username/email or password");
    }

    public record AuthTokenBundle(TokenPair tokenPair, AuthTokenResponse response) {
    }
}
