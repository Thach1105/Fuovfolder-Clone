package com.fuoverflow.auth.application;

import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.user.application.UserLookupService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class DeviceLimitEnforcer {
    private final UserSessionRepository sessions;
    private final UserLookupService users;
    private final AuthProperties authProperties;

    public DeviceLimitEnforcer(UserSessionRepository sessions, UserLookupService users,
                               AuthProperties authProperties) {
        this.sessions = sessions;
        this.users = users;
        this.authProperties = authProperties;
    }

    public void enforce(UUID userId, Instant now) {
        Short userMaxDevices = users.getMaxDevices(userId);
        int maxDevices = (userMaxDevices != null) ? userMaxDevices : authProperties.maxDevices();
        if (maxDevices <= 0) {
            return;
        }
        List<UUID> activeFamilies = sessions.findActiveFamilyIdsOrderedByAge(userId, now);
        int toRevoke = activeFamilies.size() - (maxDevices - 1);
        for (int i = 0; i < toRevoke; i++) {
            sessions.revokeFamily(activeFamilies.get(i), "DEVICE_LIMIT_EXCEEDED", now);
        }
    }
}
