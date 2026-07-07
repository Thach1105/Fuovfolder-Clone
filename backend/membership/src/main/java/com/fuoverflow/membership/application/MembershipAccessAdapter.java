package com.fuoverflow.membership.application;

import com.fuoverflow.common.membership.MembershipAccessPort;
import com.fuoverflow.membership.persistence.MembershipRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class MembershipAccessAdapter implements MembershipAccessPort {
    private final MembershipRepository membershipRepository;

    public MembershipAccessAdapter(MembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveMembership(UUID userId) {
        if (userId == null) {
            return false;
        }
        return !membershipRepository.findActiveByUserId(userId, Instant.now()).isEmpty();
    }
}
