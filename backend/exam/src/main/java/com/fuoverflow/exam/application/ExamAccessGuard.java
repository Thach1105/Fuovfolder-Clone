package com.fuoverflow.exam.application;

import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.membership.MembershipAccessPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Gates full FE/PE content behind an active membership of ANY tier.
 * Preview (first N FE questions) is handled separately in the query service.
 */
@Component
public class ExamAccessGuard {
    private final MembershipAccessPort membershipAccessPort;

    public ExamAccessGuard(MembershipAccessPort membershipAccessPort) {
        this.membershipAccessPort = membershipAccessPort;
    }

    public boolean hasActiveMembership(UUID userId) {
        return membershipAccessPort.hasActiveMembership(userId);
    }

    public void requireActiveMembership(UUID userId) {
        if (userId == null || !membershipAccessPort.hasActiveMembership(userId)) {
            throw new ForbiddenException("NO_ACTIVE_MEMBERSHIP",
                    "An active membership is required to access this content");
        }
    }
}
