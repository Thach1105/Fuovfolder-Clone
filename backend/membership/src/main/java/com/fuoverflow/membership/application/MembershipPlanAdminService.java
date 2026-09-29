package com.fuoverflow.membership.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.membership.api.dto.AdminMembershipPlanResponse;
import com.fuoverflow.membership.api.dto.CreateMembershipPlanRequest;
import com.fuoverflow.membership.api.dto.MembershipRoleOptionResponse;
import com.fuoverflow.membership.api.dto.UpdateMembershipPlanRequest;
import com.fuoverflow.membership.persistence.MembershipPlanEntity;
import com.fuoverflow.membership.persistence.MembershipPlanRepository;
import com.fuoverflow.membership.persistence.MembershipRepository;
import com.fuoverflow.membership.support.MembershipFeatures;
import com.fuoverflow.user.domain.RoleType;
import com.fuoverflow.user.persistence.RoleEntity;
import com.fuoverflow.user.persistence.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class MembershipPlanAdminService {
    private static final String DEFAULT_CURRENCY = "VND";

    private final MembershipPlanRepository planRepository;
    private final RoleRepository roleRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipRoleSyncService roleSyncService;

    public MembershipPlanAdminService(MembershipPlanRepository planRepository, RoleRepository roleRepository,
            MembershipRepository membershipRepository, MembershipRoleSyncService roleSyncService) {
        this.planRepository = planRepository;
        this.roleRepository = roleRepository;
        this.membershipRepository = membershipRepository;
        this.roleSyncService = roleSyncService;
    }

    @Transactional(readOnly = true)
    public List<AdminMembershipPlanResponse> listPlans() {
        return planRepository.findAllByOrderByPriceCentsAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminMembershipPlanResponse getPlan(UUID planId) {
        return toResponse(requirePlan(planId));
    }

    @Transactional(readOnly = true)
    public List<MembershipRoleOptionResponse> listMembershipRoles() {
        return roleRepository.findAllByOrderByRoleTypeAscSlugAsc().stream()
                .filter(role -> role.getRoleType() == RoleType.MEMBERSHIP)
                .map(role -> new MembershipRoleOptionResponse(role.getId(), role.getSlug(), role.getName()))
                .toList();
    }

    @Transactional
    public AdminMembershipPlanResponse createPlan(CreateMembershipPlanRequest request) {
        String slug = normalizeSlug(request.slug());
        if (planRepository.findBySlug(slug).isPresent()) {
            throw new ConflictException("PLAN_EXISTS", "Membership plan slug already exists");
        }
        validateRoleSlug(request.roleSlug());
        String billingInterval = normalizeBillingInterval(request.billingInterval());
        String status = normalizeStatus(request.status());
        Instant now = Instant.now();
        MembershipPlanEntity plan = MembershipPlanEntity.create(
                UUID.randomUUID(),
                slug,
                request.name().trim(),
                blankToNull(request.description()),
                request.pricePoints(),
                DEFAULT_CURRENCY,
                billingInterval,
                status,
                MembershipFeatures.buildFeaturesJson(request.roleSlug().trim(), request.durationDays()),
                now);
        plan.setImageUrl(blankToNull(request.imageUrl()));
        planRepository.save(plan);
        return toResponse(plan);
    }

    @Transactional
    public AdminMembershipPlanResponse updatePlan(UUID planId, UpdateMembershipPlanRequest request) {
        MembershipPlanEntity plan = requirePlan(planId);
        String previousRole = MembershipFeatures.roleSlug(plan.getFeaturesJson());
        validateRoleSlug(request.roleSlug());
        String billingInterval = normalizeBillingInterval(request.billingInterval());
        String status = normalizeStatus(request.status());
        plan.setName(request.name().trim());
        plan.setDescription(blankToNull(request.description()));
        plan.setPriceCents(request.pricePoints());
        plan.setBillingInterval(billingInterval);
        plan.setStatus(status);
        plan.setFeaturesJson(MembershipFeatures.buildFeaturesJson(
                request.roleSlug().trim(), request.durationDays()));
        plan.setImageUrl(blankToNull(request.imageUrl()));
        planRepository.save(plan);
        if (!java.util.Objects.equals(previousRole, request.roleSlug().trim())) {
            membershipRepository.findActiveUserIdsByPlanId(planId)
                    .forEach(roleSyncService::syncActiveMembershipRoles);
        }
        return toResponse(plan);
    }

    private MembershipPlanEntity requirePlan(UUID planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new NotFoundException("PLAN_NOT_FOUND", "Membership plan not found"));
    }

    private void validateRoleSlug(String roleSlug) {
        RoleEntity role = roleRepository.findBySlug(roleSlug.trim())
                .orElseThrow(() -> new BadRequestException("ROLE_INVALID", "Membership role not found"));
        if (role.getRoleType() != RoleType.MEMBERSHIP) {
            throw new BadRequestException("ROLE_NOT_MEMBERSHIP", "Role must be of type MEMBERSHIP");
        }
    }

    private String normalizeSlug(String slug) {
        return slug.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9-]", "");
    }

    private String normalizeBillingInterval(String billingInterval) {
        String value = billingInterval == null || billingInterval.isBlank() ? "month" : billingInterval.trim();
        if (!List.of("month", "year", "lifetime").contains(value)) {
            throw new BadRequestException("BILLING_INTERVAL_INVALID", "Billing interval must be month, year, or lifetime");
        }
        return value;
    }

    private String normalizeStatus(String status) {
        String value = status == null || status.isBlank() ? "active" : status.trim();
        if (!List.of("active", "inactive", "archived").contains(value)) {
            throw new BadRequestException("PLAN_STATUS_INVALID", "Status must be active, inactive, or archived");
        }
        return value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private AdminMembershipPlanResponse toResponse(MembershipPlanEntity plan) {
        return new AdminMembershipPlanResponse(
                plan.getId(),
                plan.getSlug(),
                plan.getName(),
                plan.getDescription(),
                plan.getPriceCents(),
                plan.getCurrency(),
                plan.getBillingInterval(),
                plan.getStatus(),
                MembershipFeatures.roleSlug(plan.getFeaturesJson()),
                MembershipFeatures.durationDays(plan.getFeaturesJson(), 30),
                plan.getImageUrl());
    }
}
