package com.fuoverflow.user.domain;

public enum UserStatus {
    PENDING_EMAIL_VERIFICATION,
    ACTIVE,
    DISABLED,
    DELETED;

    public boolean canAuthenticate() {
        return this == ACTIVE;
    }
}
