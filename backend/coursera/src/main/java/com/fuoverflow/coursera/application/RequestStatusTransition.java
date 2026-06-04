package com.fuoverflow.coursera.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.coursera.domain.RequestStatus;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public final class RequestStatusTransition {
    private static final Map<RequestStatus, EnumSet<RequestStatus>> ALLOWED = Map.of(
            RequestStatus.PENDING, EnumSet.of(RequestStatus.IN_PROGRESS, RequestStatus.CANCELLED),
            RequestStatus.IN_PROGRESS, EnumSet.of(RequestStatus.COMPLETED, RequestStatus.CANCELLED),
            RequestStatus.COMPLETED, EnumSet.noneOf(RequestStatus.class),
            RequestStatus.CANCELLED, EnumSet.noneOf(RequestStatus.class));

    private RequestStatusTransition() {
    }

    public static List<String> allowedNextDb(RequestStatus from) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(RequestStatus.class)).stream()
                .map(RequestStatus::toDb)
                .toList();
    }

    public static void validate(RequestStatus from, RequestStatus to) {
        if (from == to) {
            throw new BadRequestException("INVALID_STATUS", "Request already in status " + to.name());
        }
        if (from.isTerminal()) {
            throw new BadRequestException("INVALID_STATUS", "Cannot change status from terminal state " + from.name());
        }
        EnumSet<RequestStatus> allowed = ALLOWED.getOrDefault(from, EnumSet.noneOf(RequestStatus.class));
        if (!allowed.contains(to)) {
            throw new BadRequestException(
                    "INVALID_STATUS",
                    "Cannot transition from " + from.name() + " to " + to.name());
        }
    }
}
