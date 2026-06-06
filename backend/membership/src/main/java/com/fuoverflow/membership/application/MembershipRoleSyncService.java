package com.fuoverflow.membership.application;

import com.fuoverflow.membership.persistence.MembershipEntity;
import com.fuoverflow.membership.persistence.MembershipPlanEntity;
import com.fuoverflow.membership.persistence.MembershipPlanRepository;
import com.fuoverflow.membership.persistence.MembershipRepository;
import com.fuoverflow.membership.support.MembershipFeatures;
import com.fuoverflow.user.application.RoleAssignmentService;
import com.fuoverflow.user.domain.RoleType;
import com.fuoverflow.user.persistence.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MembershipRoleSyncService {
    private final MembershipRepository membershipRepository;
    private final MembershipPlanRepository planRepository;
    private final RoleAssignmentService roleAssignmentService;
    private final RoleRepository roleRepository;

    public MembershipRoleSyncService(
            MembershipRepository membershipRepository,
            MembershipPlanRepository planRepository,
            RoleAssignmentService roleAssignmentService,
            RoleRepository roleRepository) {
        this.membershipRepository = membershipRepository;
        this.planRepository = planRepository;
        this.roleAssignmentService = roleAssignmentService;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public void syncActiveMembershipRoles(UUID userId) {
        Instant now = Instant.now();
        List<MembershipEntity> active = membershipRepository.findActiveByUserId(userId, now);
        revokeMembershipRoles(userId);
        for (MembershipEntity membership : active) {
            planRepository.findById(membership.getPlanId()).ifPresent(plan -> {
                String roleSlug = MembershipFeatures.roleSlug(plan.getFeaturesJson());
                if (roleSlug != null && !roleSlug.isBlank()) {
                    roleAssignmentService.assignGlobalRole(userId, roleSlug, null);
                }
            });
        }
    }

    @Transactional
    public void revokeMembershipRoles(UUID userId) {
        roleRepository.findAllByOrderByRoleTypeAscSlugAsc().stream()
                .filter(role -> role.getRoleType() == RoleType.MEMBERSHIP)
                .forEach(role -> roleAssignmentService.revokeGlobalRole(userId, role.getSlug()));
    }

    @Transactional
    public void onMembershipActivated(UUID userId, MembershipPlanEntity plan) {
        String roleSlug = MembershipFeatures.roleSlug(plan.getFeaturesJson());
        if (roleSlug != null && !roleSlug.isBlank()) {
            roleAssignmentService.assignGlobalRole(userId, roleSlug, null);
        }
    }

    @Transactional
    public void onMembershipExpired(UUID userId, MembershipPlanEntity plan) {
        String roleSlug = MembershipFeatures.roleSlug(plan.getFeaturesJson());
        if (roleSlug != null && !roleSlug.isBlank()) {
            roleAssignmentService.revokeGlobalRole(userId, roleSlug);
        }
    }
}
