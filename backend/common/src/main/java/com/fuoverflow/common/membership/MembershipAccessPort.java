package com.fuoverflow.common.membership;

import java.util.UUID;

/**
 * Cross-module read port for membership status. Implemented by the membership module,
 * consumed by content modules (e.g. exam) to gate access behind an active membership.
 */
public interface MembershipAccessPort {

    /**
     * @return true if the user currently holds any active, non-expired membership.
     */
    boolean hasActiveMembership(UUID userId);
}
