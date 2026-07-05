package com.fuoverflow.auth.application;

import com.fuoverflow.auth.api.dto.SessionListResponse;
import com.fuoverflow.auth.api.dto.SessionResponse;
import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.persistence.UserSessionEntity;
import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.common.support.UserAgentParser;
import com.fuoverflow.user.application.UserLookupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SessionManagementService {
    private final UserSessionRepository sessions;
    private final UserLookupService users;
    private final AuthProperties authProperties;

    public SessionManagementService(UserSessionRepository sessions, UserLookupService users,
                                     AuthProperties authProperties) {
        this.sessions = sessions;
        this.users = users;
        this.authProperties = authProperties;
    }

    @Transactional(readOnly = true)
    public SessionListResponse listSessions(UUID userId, UUID currentFamilyId) {
        Instant now = Instant.now();
        List<UserSessionEntity> active = sessions.findActiveSessionsForUser(userId, now);

        List<SessionResponse> sessionDtos = active.stream().map(s -> {
            UserAgentParser.DeviceLabel label = UserAgentParser.parse(s.getUserAgent());
            boolean current = currentFamilyId != null && s.getRefreshTokenFamilyId().equals(currentFamilyId);
            return new SessionResponse(
                    s.getRefreshTokenFamilyId(),
                    s.getIpAddress(),
                    s.getUserAgent(),
                    label.label(),
                    s.getIssuedAt(),
                    s.getLastUsedAt(),
                    current
            );
        }).toList();

        Short userMax = users.getMaxDevices(userId);
        int maxDevices;
        String source;
        if (userMax == null) {
            maxDevices = authProperties.maxDevices();
            source = "GLOBAL";
        } else if (userMax == 0) {
            maxDevices = 0;
            source = "UNLIMITED";
        } else {
            maxDevices = userMax;
            source = "CUSTOM";
        }

        return new SessionListResponse(sessionDtos, maxDevices, source);
    }

    @Transactional
    public void revokeSession(UUID userId, UUID familyId, UUID currentFamilyId) {
        if (currentFamilyId != null && familyId.equals(currentFamilyId)) {
            throw new BadRequestException("CANNOT_REVOKE_CURRENT", "Không thể đăng xuất phiên hiện tại");
        }
        int revoked = sessions.revokeFamily(familyId, "REMOTE_LOGOUT", Instant.now());
        if (revoked == 0) {
            throw new NotFoundException("SESSION_NOT_FOUND", "Không tìm thấy phiên đăng nhập");
        }
    }

    @Transactional
    public void revokeOtherSessions(UUID userId, UUID currentFamilyId) {
        sessions.revokeAllExceptFamily(userId, currentFamilyId, "REMOTE_LOGOUT_ALL", Instant.now());
    }

    @Transactional
    public void adminRevokeSession(UUID familyId) {
        int revoked = sessions.revokeFamily(familyId, "ADMIN_REVOKE", Instant.now());
        if (revoked == 0) {
            throw new NotFoundException("SESSION_NOT_FOUND", "Không tìm thấy phiên đăng nhập");
        }
    }

    @Transactional
    public void adminRevokeAllSessions(UUID userId) {
        sessions.revokeAllByUserId(userId, "ADMIN_REVOKE_ALL", Instant.now());
    }
}
