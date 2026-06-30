package com.fuoverflow.voucher.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.voucher.api.dto.VoucherRedemptionResponse;
import com.fuoverflow.voucher.api.dto.VoucherResponse;
import com.fuoverflow.voucher.persistence.VoucherEntity;
import com.fuoverflow.voucher.persistence.VoucherRedemptionRepository;
import com.fuoverflow.voucher.persistence.VoucherRepository;
import com.fuoverflow.voucher.persistence.VoucherUserAssignmentEntity;
import com.fuoverflow.voucher.persistence.VoucherUserAssignmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class VoucherAdminService {
    private static final Set<String> VALID_DISCOUNT_TYPES = Set.of("percentage", "fixed");
    private static final Set<String> VALID_APPLICABLE_TYPES = Set.of("source", "membership", "coursera");

    private final VoucherRepository voucherRepository;
    private final VoucherUserAssignmentRepository assignmentRepository;
    private final VoucherRedemptionRepository redemptionRepository;

    public VoucherAdminService(
            VoucherRepository voucherRepository,
            VoucherUserAssignmentRepository assignmentRepository,
            VoucherRedemptionRepository redemptionRepository) {
        this.voucherRepository = voucherRepository;
        this.assignmentRepository = assignmentRepository;
        this.redemptionRepository = redemptionRepository;
    }

    @Transactional(readOnly = true)
    public Page<VoucherResponse> listAll(int page, int size) {
        return voucherRepository.findAll(PageRequest.of(page, Math.min(size, 100)))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public VoucherResponse get(UUID id) {
        return toResponse(requireVoucher(id));
    }

    @Transactional
    public VoucherResponse create(String code, String description, String discountType,
                                   int discountValue, Integer maxDiscountPoints, int minOrderPoints,
                                   int maxUsage, int maxUsagePerUser, String applicableTypes,
                                   String requiredMembershipSlugs, Instant startsAt, Instant endsAt,
                                   UUID createdBy) {
        validateDiscountType(discountType);
        validateApplicableTypes(applicableTypes);
        if (voucherRepository.findByCodeIgnoreCase(code.trim()).isPresent()) {
            throw new ConflictException("VOUCHER_CODE_EXISTS", "Mã voucher đã tồn tại");
        }
        if ("percentage".equals(discountType) && discountValue > 100) {
            throw new BadRequestException("INVALID_PERCENTAGE", "Phần trăm giảm giá không được vượt quá 100");
        }
        Instant now = Instant.now();
        VoucherEntity entity = VoucherEntity.create(
                UUID.randomUUID(), code.trim(), blankToNull(description), discountType,
                discountValue, maxDiscountPoints, minOrderPoints, maxUsage, maxUsagePerUser,
                applicableTypes.trim(), blankToNull(requiredMembershipSlugs),
                startsAt, endsAt, createdBy, now);
        voucherRepository.save(entity);
        return toResponse(entity);
    }

    @Transactional
    public VoucherResponse update(UUID id, String code, String description, String discountType,
                                   int discountValue, Integer maxDiscountPoints, int minOrderPoints,
                                   int maxUsage, int maxUsagePerUser, String applicableTypes,
                                   String requiredMembershipSlugs, Instant startsAt, Instant endsAt) {
        VoucherEntity entity = requireVoucher(id);
        validateDiscountType(discountType);
        validateApplicableTypes(applicableTypes);
        var existing = voucherRepository.findByCodeIgnoreCase(code.trim());
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new ConflictException("VOUCHER_CODE_EXISTS", "Mã voucher đã tồn tại");
        }
        if ("percentage".equals(discountType) && discountValue > 100) {
            throw new BadRequestException("INVALID_PERCENTAGE", "Phần trăm giảm giá không được vượt quá 100");
        }
        entity.setCode(code.trim().toUpperCase());
        entity.setDescription(blankToNull(description));
        entity.setDiscountType(discountType);
        entity.setDiscountValue(discountValue);
        entity.setMaxDiscountPoints(maxDiscountPoints);
        entity.setMinOrderPoints(minOrderPoints);
        entity.setMaxUsage(maxUsage);
        entity.setMaxUsagePerUser(maxUsagePerUser);
        entity.setApplicableTypes(applicableTypes.trim());
        entity.setRequiredMembershipSlugs(blankToNull(requiredMembershipSlugs));
        entity.setStartsAt(startsAt);
        entity.setEndsAt(endsAt);
        entity.setUpdatedAt(Instant.now());
        voucherRepository.save(entity);
        return toResponse(entity);
    }

    @Transactional
    public VoucherResponse toggleActive(UUID id) {
        VoucherEntity entity = requireVoucher(id);
        entity.setActive(!entity.isActive());
        entity.setUpdatedAt(Instant.now());
        voucherRepository.save(entity);
        return toResponse(entity);
    }

    @Transactional
    public void assignUsers(UUID voucherId, List<UUID> userIds) {
        requireVoucher(voucherId);
        Instant now = Instant.now();
        for (UUID userId : userIds) {
            if (!assignmentRepository.existsByVoucherIdAndUserId(voucherId, userId)) {
                assignmentRepository.save(VoucherUserAssignmentEntity.create(
                        UUID.randomUUID(), voucherId, userId, now));
            }
        }
    }

    @Transactional
    public void removeAssignment(UUID voucherId, UUID userId) {
        requireVoucher(voucherId);
        assignmentRepository.deleteByVoucherIdAndUserId(voucherId, userId);
    }

    @Transactional(readOnly = true)
    public Page<VoucherRedemptionResponse> listRedemptions(UUID voucherId, int page, int size) {
        requireVoucher(voucherId);
        return redemptionRepository.findByVoucherIdOrderByCreatedAtDesc(
                voucherId, PageRequest.of(page, Math.min(size, 100)))
                .map(r -> new VoucherRedemptionResponse(
                        r.getId(), r.getVoucherId(), r.getUserId(), r.getTransactionType(),
                        r.getTransactionId(), r.getOriginalPoints(), r.getDiscountPoints(),
                        r.getFinalPoints(), r.getCreatedAt()));
    }

    private VoucherEntity requireVoucher(UUID id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("VOUCHER_NOT_FOUND", "Voucher không tồn tại"));
    }

    private void validateDiscountType(String discountType) {
        if (!VALID_DISCOUNT_TYPES.contains(discountType)) {
            throw new BadRequestException("INVALID_DISCOUNT_TYPE", "Discount type phải là percentage hoặc fixed");
        }
    }

    private void validateApplicableTypes(String applicableTypes) {
        for (String type : applicableTypes.split(",")) {
            if (!VALID_APPLICABLE_TYPES.contains(type.trim())) {
                throw new BadRequestException("INVALID_APPLICABLE_TYPE",
                        "Applicable type không hợp lệ: " + type.trim());
            }
        }
    }

    private VoucherResponse toResponse(VoucherEntity e) {
        return new VoucherResponse(
                e.getId(), e.getCode(), e.getDescription(), e.getDiscountType(),
                e.getDiscountValue(), e.getMaxDiscountPoints(), e.getMinOrderPoints(),
                e.getMaxUsage(), e.getUsedCount(), e.getMaxUsagePerUser(),
                e.getApplicableTypes(), e.getRequiredMembershipSlugs(),
                e.getStartsAt(), e.getEndsAt(), e.isActive(),
                e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
