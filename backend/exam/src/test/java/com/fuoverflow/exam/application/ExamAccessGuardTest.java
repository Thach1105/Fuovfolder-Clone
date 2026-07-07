package com.fuoverflow.exam.application;

import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.membership.MembershipAccessPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamAccessGuardTest {
    @Mock
    private MembershipAccessPort membershipAccessPort;

    private ExamAccessGuard guard;
    private UUID userId;

    @BeforeEach
    void setUp() {
        guard = new ExamAccessGuard(membershipAccessPort);
        userId = UUID.randomUUID();
    }

    @Test
    void requireActiveMembership_passesForActiveMember() {
        when(membershipAccessPort.hasActiveMembership(userId)).thenReturn(true);
        assertDoesNotThrow(() -> guard.requireActiveMembership(userId));
        assertTrue(guard.hasActiveMembership(userId));
    }

    @Test
    void requireActiveMembership_throwsForNonMember() {
        when(membershipAccessPort.hasActiveMembership(userId)).thenReturn(false);
        assertThrows(ForbiddenException.class, () -> guard.requireActiveMembership(userId));
    }

    @Test
    void requireActiveMembership_throwsForAnonymous() {
        assertThrows(ForbiddenException.class, () -> guard.requireActiveMembership(null));
        assertFalse(guard.hasActiveMembership(null));
    }
}
