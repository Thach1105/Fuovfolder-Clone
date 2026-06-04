package com.fuoverflow.coursera.domain;

import java.util.EnumSet;
import java.util.Set;

public enum RequestStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    private static final Set<RequestStatus> TERMINAL = EnumSet.of(COMPLETED, CANCELLED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public static RequestStatus fromDb(String value) {
        return RequestStatus.valueOf(value.toUpperCase());
    }

    public String toDb() {
        return name().toLowerCase();
    }
}
