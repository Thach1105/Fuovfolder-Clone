package com.fuoverflow.membership.application;

import com.fuoverflow.award.application.PointsWalletService;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.membership.api.dto.MembershipPlanResponse;
import com.fuoverflow.membership.api.dto.MembershipStatusResponse;
import com.fuoverflow.membership.api.dto.SubscribeMembershipRequest;
import com.fuoverflow.membership.persistence.MembershipEntity;
import com.fuoverflow.membership.persistence.MembershipPlanEntity;
import com.fuoverflow.membership.persistence.MembershipPlanRepository;
import com.fuoverflow.membership.persistence.MembershipRepository;
import com.fuoverflow.common.voucher.VoucherDiscountResult;
import com.fuoverflow.common.voucher.VoucherRedemptionPort;
import com.fuoverflow.membership.support.MembershipFeatures;
import com.fuoverflow.user.domain.UserStatus;
import com.fuoverflow.user.persistence.UserEntity;
import com.fuoverflow.user.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class MembershipService {
    public static final String SOURCE_MEMBERSHIP = "membership";

    private final MembershipPlanRepository planRepository;
    private final MembershipRepository membershipRepository;
    private final PointsWalletService walletService;
    private final UserRepository userRepository;
    private final MembershipRoleSyncService roleSyncService;
    private final VoucherRedemptionPort voucherRedemptionPort;

    public MembershipService(
            MembershipPlanRepository planRepository,
            MembershipRepository membershipRepository,
            PointsWalletService walletService,
            UserRepository userRepository,
            MembershipRoleSyncService roleSyncService,
            VoucherRedemptionPort voucherRedemptionPort) {
        this.planRepository = planRepository;
        this.membershipRepository = membershipRepository;
        this.walletService = walletService;
        this.userRepository = userRepository;
        this.roleSyncService = roleSyncService;
        this.voucherRedemptionPort = voucherRedemptionPort;
    }

    @Transactional(readOnly = true)
    public List<MembershipPlanResponse> listPlans() {
        return planRepository.findByStatusOrderByPriceCentsAsc("active").stream()
                .map(this::toPlanResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MembershipStatusResponse currentMembership(UUID userId) {
        Instant now = Instant.now();
        return membershipRepository.findActiveByUserId(userId, now).stream()
                .findFirst()
                .flatMap(m -> planRepository.findById(m.getPlanId()).map(plan -> toStatus(m, plan)))
                .orElse(new MembershipStatusResponse(null, null, null, null, null, false));
    }

    @Transactional
    public MembershipStatusResponse subscribe(UUID userId, SubscribeMembershipRequest request) {
        requireEligibleUser(userId);
        MembershipPlanEntity plan = planRepository.findBySlugAndStatus(request.planSlug().trim(), "active")
                .orElseThrow(() -> new NotFoundException("PLAN_NOT_FOUND", "Membership plan not found"));
        int price = plan.getPriceCents();
        if (price <= 0) {
            throw new BadRequestException("PLAN_INVALID", "Plan price is invalid");
        }
        Instant now = Instant.now();
        membershipRepository.findActiveByUserId(userId, now).stream().findFirst().ifPresent(existing -> {
            throw new ConflictException("MEMBERSHIP_ACTIVE", "You already have an active membership");
        });

        UUID membershipId = UUID.randomUUID();

        if (request.voucherCode() != null && !request.voucherCode().isBlank()) {
            VoucherDiscountResult voucherResult = voucherRedemptionPort.redeem(
                    request.voucherCode(), userId, "membership", membershipId, price);
            price = voucherResult.finalPoints();
        }

        if (price > 0) {
            walletService.debit(userId, price, "Membership: " + plan.getName(), SOURCE_MEMBERSHIP, membershipId);
        }

        int durationDays = MembershipFeatures.durationDays(plan.getFeaturesJson(), 30);
        Instant endsAt = "lifetime".equals(plan.getBillingInterval())
                ? null : now.plus(durationDays, ChronoUnit.DAYS);
        MembershipEntity membership = MembershipEntity.create(membershipId, userId, plan.getId(), now, endsAt, now);
        membershipRepository.save(membership);
        roleSyncService.onMembershipActivated(userId, plan);
        return toStatus(membership, plan);
    }

    private void requireEligibleUser(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new BadRequestException("USER_NOT_FOUND", "User not found"));
        if (user.getStatus() != UserStatus.ACTIVE || user.getEmailVerifiedAt() == null) {
            throw new ForbiddenException("USER_NOT_ELIGIBLE", "Account must be active with verified email");
        }
    }

    private MembershipPlanResponse toPlanResponse(MembershipPlanEntity plan) {
        return new MembershipPlanResponse(
                plan.getId(),
                plan.getSlug(),
                plan.getName(),
                plan.getDescription(),
                plan.getPriceCents(),
                plan.getCurrency(),
                plan.getBillingInterval(),
                MembershipFeatures.roleSlug(plan.getFeaturesJson()),
                MembershipFeatures.durationDays(plan.getFeaturesJson(), 30),
                plan.getImageUrl());
    }

    private MembershipStatusResponse toStatus(MembershipEntity membership, MembershipPlanEntity plan) {
        return new MembershipStatusResponse(
                membership.getId(),
                plan.getSlug(),
                plan.getName(),
                MembershipFeatures.roleSlug(plan.getFeaturesJson()),
                membership.getCurrentPeriodEnd(),
                "active".equals(membership.getStatus()));
    }
}
