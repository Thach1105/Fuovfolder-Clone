package com.fuoverflow.user.domain;

public enum UserStatus {
    PENDING_EMAIL_VERIFICATION,
    ACTIVE,
    DISABLED,
    DELETED,
    PENDING_PROFILE;

    public boolean canAuthenticate() {
        return this == ACTIVE || this == PENDING_PROFILE;
    }
}
