package com.fuoverflow.user.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserStatusTest {

    @Test
    void shouldHaveExpectedEnumValues() {
        // Verify all expected statuses exist, including the new PENDING_PROFILE
        UserStatus[] values = UserStatus.values();

        assertEquals(5, values.length, "Should have 5 status values");

        // Verify each expected value exists
        assertNotNull(UserStatus.valueOf("PENDING_EMAIL_VERIFICATION"));
        assertNotNull(UserStatus.valueOf("ACTIVE"));
        assertNotNull(UserStatus.valueOf("DISABLED"));
        assertNotNull(UserStatus.valueOf("DELETED"));
        assertNotNull(UserStatus.valueOf("PENDING_PROFILE"));
    }

    @Test
    void shouldOnlyAllowActiveUsersToAuthenticate() {
        assertTrue(UserStatus.ACTIVE.canAuthenticate(),
            "ACTIVE users should be able to authenticate");

        assertFalse(UserStatus.PENDING_EMAIL_VERIFICATION.canAuthenticate(),
            "PENDING_EMAIL_VERIFICATION users should not be able to authenticate");
        assertFalse(UserStatus.DISABLED.canAuthenticate(),
            "DISABLED users should not be able to authenticate");
        assertFalse(UserStatus.DELETED.canAuthenticate(),
            "DELETED users should not be able to authenticate");
        assertFalse(UserStatus.PENDING_PROFILE.canAuthenticate(),
            "PENDING_PROFILE users should not be able to authenticate");
    }

    @Test
    void pendingProfileShouldExist() {
        // Explicit test for the new PENDING_PROFILE status
        UserStatus status = UserStatus.PENDING_PROFILE;

        assertNotNull(status, "PENDING_PROFILE status should exist");
        assertEquals("PENDING_PROFILE", status.name());
        assertFalse(status.canAuthenticate(),
            "PENDING_PROFILE users should not be able to authenticate");
    }
}
