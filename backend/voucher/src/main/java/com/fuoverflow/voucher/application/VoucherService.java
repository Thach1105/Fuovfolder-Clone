package com.fuoverflow.voucher.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.membership.persistence.MembershipEntity;
import com.fuoverflow.membership.persistence.MembershipPlanRepository;
import com.fuoverflow.membership.persistence.MembershipRepository;
import com.fuoverflow.common.voucher.VoucherDiscountResult;
import com.fuoverflow.common.voucher.VoucherRedemptionPort;
import com.fuoverflow.voucher.persistence.VoucherEntity;
import com.fuoverflow.voucher.persistence.VoucherRedemptionEntity;
import com.fuoverflow.voucher.persistence.VoucherRedemptionRepository;
import com.fuoverflow.voucher.persistence.VoucherRepository;
import com.fuoverflow.voucher.persistence.VoucherUserAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VoucherService implements VoucherRedemptionPort {

    private final VoucherRepository voucherRepository;
    private final VoucherRedemptionRepository redemptionRepository;
    private final VoucherUserAssignmentRepository assignmentRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipPlanRepository membershipPlanRepository;

    public VoucherService(
            VoucherRepository voucherRepository,
            VoucherRedemptionRepository redemptionRepository,
            VoucherUserAssignmentRepository assignmentRepository,
            MembershipRepository membershipRepository,
            MembershipPlanRepository membershipPlanRepository) {
        this.voucherRepository = voucherRepository;
        this.redemptionRepository = redemptionRepository;
        this.assignmentRepository = assignmentRepository;
        this.membershipRepository = membershipRepository;
        this.membershipPlanRepository = membershipPlanRepository;
    }

    @Transactional(readOnly = true)
    public VoucherDiscountResult preview(String code, UUID userId, String transactionType, int originalPoints) {
        return validate(code, userId, transactionType, originalPoints);
    }

    @Transactional
    public VoucherDiscountResult redeem(String code, UUID userId, String transactionType,
                                        UUID transactionId, int originalPoints) {
        VoucherDiscountResult result = validate(code, userId, transactionType, originalPoints);
        if (!result.valid()) {
            throw new BadRequestException("VOUCHER_INVALID", result.message());
        }
        int updated = voucherRepository.atomicIncrementUsedCount(result.voucherId());
        if (updated == 0) {
            throw new ConflictException("VOUCHER_EXHAUSTED", "Voucher vừa hết, vui lòng thử voucher khác");
        }
        redemptionRepository.save(VoucherRedemptionEntity.create(
                UUID.randomUUID(), result.voucherId(), userId, transactionType,
                transactionId, originalPoints, result.discountPoints(), result.finalPoints(),
                Instant.now()));
        return result;
    }

    private VoucherDiscountResult validate(String code, UUID userId, String transactionType, int originalPoints) {
        if (code == null || code.isBlank()) {
            return VoucherDiscountResult.invalid("Mã voucher không được để trống");
        }
        VoucherEntity voucher = voucherRepository.findActiveByCode(code.trim())
                .orElseThrow(() -> new NotFoundException("VOUCHER_NOT_FOUND", "Mã voucher không hợp lệ"));

        Instant now = Instant.now();

        if (now.isBefore(voucher.getStartsAt())) {
            return VoucherDiscountResult.invalid("Voucher chưa có hiệu lực");
        }
        if (now.isAfter(voucher.getEndsAt())) {
            return VoucherDiscountResult.invalid("Voucher đã hết hạn");
        }
        if (voucher.getUsedCount() >= voucher.getMaxUsage()) {
            return VoucherDiscountResult.invalid("Voucher đã hết lượt sử dụng");
        }

        Set<String> types = Arrays.stream(voucher.getApplicableTypes().split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
        if (!types.contains(transactionType)) {
            return VoucherDiscountResult.invalid("Voucher không áp dụng cho loại giao dịch này");
        }

        if (originalPoints < voucher.getMinOrderPoints()) {
            return VoucherDiscountResult.invalid(
                    "Đơn hàng cần tối thiểu " + voucher.getMinOrderPoints() + " points");
        }

        long userUsage = redemptionRepository.countByVoucherIdAndUserId(voucher.getId(), userId);
        if (userUsage >= voucher.getMaxUsagePerUser()) {
            return VoucherDiscountResult.invalid("Bạn đã sử dụng hết lượt cho voucher này");
        }

        if (voucher.getRequiredMembershipSlugs() != null && !voucher.getRequiredMembershipSlugs().isBlank()) {
            Set<String> requiredSlugs = Arrays.stream(voucher.getRequiredMembershipSlugs().split(","))
                    .map(String::trim)
                    .collect(Collectors.toSet());
            List<MembershipEntity> activeMemberships = membershipRepository.findActiveByUserId(userId, now);
            List<UUID> planIds = activeMemberships.stream().map(MembershipEntity::getPlanId).toList();
            boolean hasRequired = membershipPlanRepository.findAllById(planIds).stream()
                    .anyMatch(plan -> requiredSlugs.contains(plan.getSlug()));
            if (!hasRequired) {
                return VoucherDiscountResult.invalid("Voucher yêu cầu membership " +
                        String.join(", ", requiredSlugs));
            }
        }

        long assignmentCount = assignmentRepository.countByVoucherId(voucher.getId());
        if (assignmentCount > 0 && !assignmentRepository.existsByVoucherIdAndUserId(voucher.getId(), userId)) {
            throw new ForbiddenException("VOUCHER_NOT_ASSIGNED", "Voucher này không dành cho bạn");
        }

        int discount = calculateDiscount(voucher, originalPoints);
        int finalPoints = originalPoints - discount;
        String message = "percentage".equals(voucher.getDiscountType())
                ? "Giảm " + voucher.getDiscountValue() + "%"
                        + (voucher.getMaxDiscountPoints() != null ? ", tối đa " + voucher.getMaxDiscountPoints() + " points" : "")
                : "Giảm " + voucher.getDiscountValue() + " points";

        return VoucherDiscountResult.success(voucher.getId(), discount, finalPoints, message);
    }

    private int calculateDiscount(VoucherEntity voucher, int originalPoints) {
        if ("percentage".equals(voucher.getDiscountType())) {
            int discount = (int) ((long) originalPoints * voucher.getDiscountValue() / 100);
            if (voucher.getMaxDiscountPoints() != null) {
                discount = Math.min(discount, voucher.getMaxDiscountPoints());
            }
            return Math.min(discount, originalPoints);
        } else {
            return Math.min(voucher.getDiscountValue(), originalPoints);
        }
    }
}
