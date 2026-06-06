package com.fuoverflow.membership.application;

import com.fuoverflow.membership.persistence.MembershipEntity;
import com.fuoverflow.membership.persistence.MembershipPlanRepository;
import com.fuoverflow.membership.persistence.MembershipRepository;
import com.fuoverflow.user.application.PermissionResolverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Marks elapsed memberships as expired and revokes linked membership roles so RBAC
 * stays aligned without checking {@code current_period_end} on every request.
 */
@Service
public class MembershipExpiryService {
    private static final Logger log = LoggerFactory.getLogger(MembershipExpiryService.class);

    private final MembershipRepository membershipRepository;
    private final MembershipPlanRepository planRepository;
    private final MembershipRoleSyncService roleSyncService;
    private final PermissionResolverService permissionResolver;

    public MembershipExpiryService(
            MembershipRepository membershipRepository,
            MembershipPlanRepository planRepository,
            MembershipRoleSyncService roleSyncService,
            PermissionResolverService permissionResolver) {
        this.membershipRepository = membershipRepository;
        this.planRepository = planRepository;
        this.roleSyncService = roleSyncService;
        this.permissionResolver = permissionResolver;
    }

    @Scheduled(cron = "${fuoverflow.membership.expiry-cron:0 0 0 * * *}",
            zone = "${fuoverflow.scheduling.timezone:Asia/Ho_Chi_Minh}")
    @Transactional
    public void expireElapsedMemberships() {
        Instant now = Instant.now();
        List<MembershipEntity> expired = membershipRepository.findExpiredActive(now);
        if (expired.isEmpty()) {
            return;
        }
        int processed = 0;
        for (MembershipEntity membership : expired) {
            if (expireOne(membership, now)) {
                processed++;
            }
        }
        if (processed > 0) {
            log.info("Expired {} membership subscription(s)", processed);
        }
    }

    private boolean expireOne(MembershipEntity membership, Instant now) {
        return planRepository.findById(membership.getPlanId()).map(plan -> {
            membership.setStatus("expired");
            membership.setUpdatedAt(now);
            membershipRepository.save(membership);
            roleSyncService.onMembershipExpired(membership.getUserId(), plan);
            permissionResolver.bumpVersion(membership.getUserId());
            return true;
        }).orElseGet(() -> {
            log.warn("Membership {} references missing plan {}", membership.getId(), membership.getPlanId());
            membership.setStatus("expired");
            membership.setUpdatedAt(now);
            membershipRepository.save(membership);
            permissionResolver.bumpVersion(membership.getUserId());
            return true;
        });
    }
}
