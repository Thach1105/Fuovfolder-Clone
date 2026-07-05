package com.fuoverflow.auth.application;

import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.user.application.UserLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceLimitEnforcerTest {
    @Mock private UserSessionRepository sessions;
    @Mock private UserLookupService users;
    @Mock private AuthProperties authProperties;

    private DeviceLimitEnforcer enforcer;

    @BeforeEach
    void setUp() {
        enforcer = new DeviceLimitEnforcer(sessions, users, authProperties);
    }

    @Test
    void enforce_userMaxDevicesNull_usesGlobalConfig() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        UUID family1 = UUID.randomUUID();
        UUID family2 = UUID.randomUUID();
        UUID family3 = UUID.randomUUID();

        when(users.getMaxDevices(userId)).thenReturn(null);
        when(authProperties.maxDevices()).thenReturn(2);
        when(sessions.findActiveFamilyIdsOrderedByAge(userId, now))
                .thenReturn(List.of(family1, family2, family3));

        enforcer.enforce(userId, now);

        verify(sessions).revokeFamily(family1, "DEVICE_LIMIT_EXCEEDED", now);
        verify(sessions).revokeFamily(family2, "DEVICE_LIMIT_EXCEEDED", now);
        verify(sessions, never()).revokeFamily(eq(family3), any(), any());
    }

    @Test
    void enforce_userMaxDevicesZero_skipsEnforcement() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        when(users.getMaxDevices(userId)).thenReturn((short) 0);

        enforcer.enforce(userId, now);

        verify(sessions, never()).findActiveFamilyIdsOrderedByAge(any(), any());
        verify(sessions, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void enforce_userMaxDevicesCustom_usesCustomValue() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        UUID family1 = UUID.randomUUID();
        UUID family2 = UUID.randomUUID();
        UUID family3 = UUID.randomUUID();

        when(users.getMaxDevices(userId)).thenReturn((short) 5);
        when(sessions.findActiveFamilyIdsOrderedByAge(userId, now))
                .thenReturn(List.of(family1, family2, family3));

        enforcer.enforce(userId, now);

        verify(sessions, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void enforce_noActiveFamilies_doesNothing() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        when(users.getMaxDevices(userId)).thenReturn(null);
        when(authProperties.maxDevices()).thenReturn(2);
        when(sessions.findActiveFamilyIdsOrderedByAge(userId, now))
                .thenReturn(List.of());

        enforcer.enforce(userId, now);

        verify(sessions, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void enforce_exactlyAtLimit_doesNotRevoke() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        UUID family1 = UUID.randomUUID();

        when(users.getMaxDevices(userId)).thenReturn(null);
        when(authProperties.maxDevices()).thenReturn(2);
        when(sessions.findActiveFamilyIdsOrderedByAge(userId, now))
                .thenReturn(List.of(family1));

        enforcer.enforce(userId, now);

        verify(sessions, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void enforce_globalConfigDisabled_skipsEnforcement() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        when(users.getMaxDevices(userId)).thenReturn(null);
        when(authProperties.maxDevices()).thenReturn(0);

        enforcer.enforce(userId, now);

        verify(sessions, never()).findActiveFamilyIdsOrderedByAge(any(), any());
        verify(sessions, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void enforce_overLimitByMany_revokesOldestFirst() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        UUID f1 = UUID.randomUUID();
        UUID f2 = UUID.randomUUID();
        UUID f3 = UUID.randomUUID();
        UUID f4 = UUID.randomUUID();
        UUID f5 = UUID.randomUUID();

        when(users.getMaxDevices(userId)).thenReturn((short) 2);
        when(sessions.findActiveFamilyIdsOrderedByAge(userId, now))
                .thenReturn(List.of(f1, f2, f3, f4, f5));

        enforcer.enforce(userId, now);

        verify(sessions).revokeFamily(f1, "DEVICE_LIMIT_EXCEEDED", now);
        verify(sessions).revokeFamily(f2, "DEVICE_LIMIT_EXCEEDED", now);
        verify(sessions).revokeFamily(f3, "DEVICE_LIMIT_EXCEEDED", now);
        verify(sessions).revokeFamily(f4, "DEVICE_LIMIT_EXCEEDED", now);
        verify(sessions, never()).revokeFamily(eq(f5), any(), any());
    }
}
