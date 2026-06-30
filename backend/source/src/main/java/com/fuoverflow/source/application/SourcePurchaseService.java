package com.fuoverflow.source.application;

import com.fuoverflow.award.application.PointsWalletService;
import com.fuoverflow.award.persistence.PointsLedgerEntity;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.source.api.dto.PurchasePageResponse;
import com.fuoverflow.source.api.dto.PurchaseResponse;
import com.fuoverflow.source.api.dto.PurchaseStatsResponse;
import com.fuoverflow.source.config.SourceProperties;
import com.fuoverflow.source.persistence.SourceCatalogItemEntity;
import com.fuoverflow.voucher.application.VoucherService;
import com.fuoverflow.voucher.domain.VoucherDiscountResult;
import com.fuoverflow.source.persistence.SourceCatalogItemRepository;
import com.fuoverflow.source.persistence.SourcePurchaseEntity;
import com.fuoverflow.source.persistence.SourcePurchaseEventEntity;
import com.fuoverflow.source.persistence.SourcePurchaseEventRepository;
import com.fuoverflow.source.persistence.SourcePurchaseRepository;
import com.fuoverflow.user.domain.UserStatus;
import com.fuoverflow.user.persistence.UserEntity;
import com.fuoverflow.user.persistence.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class SourcePurchaseService {
    private static final int MAX_PAGE_SIZE = 100;

    private final SourcePurchaseRepository purchaseRepository;
    private final SourcePurchaseEventRepository eventRepository;
    private final SourceCatalogItemRepository catalogRepository;
    private final UserRepository userRepository;
    private final PointsWalletService walletService;
    private final SourceProperties properties;
    private final VoucherService voucherService;

    public SourcePurchaseService(
            SourcePurchaseRepository purchaseRepository,
            SourcePurchaseEventRepository eventRepository,
            SourceCatalogItemRepository catalogRepository,
            UserRepository userRepository,
            PointsWalletService walletService,
            SourceProperties properties,
            VoucherService voucherService) {
        this.purchaseRepository = purchaseRepository;
        this.eventRepository = eventRepository;
        this.catalogRepository = catalogRepository;
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.properties = properties;
        this.voucherService = voucherService;
    }

    @Transactional
    public PurchaseResponse purchase(UUID userId, UUID catalogItemId, String idempotencyKey, String voucherCode) {
        requireEligibleUser(userId);
        String key = trimToNull(idempotencyKey);
        if (key != null) {
            Optional<SourcePurchaseEntity> existing = purchaseRepository.findByUserIdAndIdempotencyKey(userId, key);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        SourceCatalogItemEntity catalog = catalogRepository.findByIdAndDeletedAtIsNull(catalogItemId)
                .filter(SourceCatalogItemEntity::isActive)
                .orElseThrow(() -> new NotFoundException("CATALOG_NOT_FOUND", "Source item not found or inactive"));

        Instant now = Instant.now();
        Optional<SourcePurchaseEntity> activeExisting = purchaseRepository
                .findFirstByUserIdAndCatalogItemIdAndStatusOrderByEndsAtDesc(userId, catalogItemId, "active")
                .filter(p -> p.getEndsAt().isAfter(now));
        if (activeExisting.isPresent()) {
            throw new ConflictException(
                    "ACTIVE_ACCESS_REMAINS",
                    "Source này vẫn còn thời gian sử dụng");
        }

        UUID purchaseId = UUID.randomUUID();
        int price = catalog.getPricePoints();
        if (voucherCode != null && !voucherCode.isBlank()) {
            VoucherDiscountResult voucherResult = voucherService.redeem(voucherCode, userId, "source", purchaseId, price);
            price = voucherResult.finalPoints();
        }
        UUID paymentLedgerId = null;
        if (price > 0) {
            PointsLedgerEntity payment = walletService.debit(
                    userId,
                    price,
                    "Suộc: " + catalog.getCode(),
                    PointsWalletService.SOURCE_SOURCE_PURCHASE,
                    purchaseId);
            paymentLedgerId = payment.getId();
        }

        Instant endsAt = now.plus(catalog.getAccessDays(), ChronoUnit.DAYS);
        SourcePurchaseEntity purchase = SourcePurchaseEntity.createActive(
                purchaseId,
                userId,
                catalog.getId(),
                catalog.getCode(),
                catalog.getTitle(),
                price,
                catalog.getAccessDays(),
                now,
                endsAt,
                key,
                now);
        purchase.setPaymentLedgerId(paymentLedgerId);
        try {
            purchaseRepository.saveAndFlush(purchase);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent request with same idempotency key won the race; return the persisted one.
            if (key != null) {
                return purchaseRepository.findByUserIdAndIdempotencyKey(userId, key)
                        .map(this::toResponse)
                        .orElseThrow(() -> ex);
            }
            throw ex;
        }

        eventRepository.save(SourcePurchaseEventEntity.create(
                UUID.randomUUID(), purchaseId, "purchase", null, "active", userId, "Purchase created", now));
        return toResponse(purchase);
    }

    @Transactional(readOnly = true)
    public PurchasePageResponse listMine(UUID userId, String filter, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Instant now = Instant.now();
        PageRequest pageable = PageRequest.of(safePage, safeSize);
        Page<SourcePurchaseEntity> result = switch (filter == null ? "all" : filter.trim().toLowerCase()) {
            case "active" -> purchaseRepository.findActiveForUser(userId, now, pageable);
            case "expired" -> purchaseRepository.findExpiredForUser(userId, now, pageable);
            default -> purchaseRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        };
        return new PurchasePageResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PurchaseStatsResponse stats(UUID userId) {
        requireUserExists(userId);
        Instant now = Instant.now();
        long active = purchaseRepository.countActiveForUser(userId, now);
        long expired = purchaseRepository.countExpiredForUser(userId, now);
        long refunded = purchaseRepository.countRefundedForUser(userId);
        long total = active + expired + refunded;
        return new PurchaseStatsResponse(total, active, expired, refunded);
    }

    @Transactional(readOnly = true)
    public PurchaseResponse getMine(UUID userId, UUID purchaseId) {
        SourcePurchaseEntity purchase = purchaseRepository.findByIdAndUserId(purchaseId, userId)
                .orElseThrow(() -> new NotFoundException("PURCHASE_NOT_FOUND", "Purchase not found"));
        return toResponse(purchase);
    }

    private PurchaseResponse toResponse(SourcePurchaseEntity p) {
        boolean active = "active".equals(p.getStatus()) && p.getEndsAt().isAfter(Instant.now());
        return new PurchaseResponse(
                p.getId(),
                p.getCatalogItemId(),
                p.getCodeSnapshot(),
                p.getTitleSnapshot(),
                p.getStatus(),
                p.getUnitPricePoints(),
                p.getAccessDaysSnapshot(),
                p.getStartsAt(),
                p.getEndsAt(),
                active,
                p.getCreatedAt());
    }

    private void requireEligibleUser(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new BadRequestException("USER_NOT_FOUND", "User not found"));
        if (user.getStatus() != UserStatus.ACTIVE || user.getEmailVerifiedAt() == null) {
            throw new ForbiddenException("USER_NOT_ELIGIBLE", "Account must be active with verified email");
        }
    }

    private void requireUserExists(UUID userId) {
        if (!userRepository.existsByIdAndDeletedAtIsNull(userId)) {
            throw new BadRequestException("USER_NOT_FOUND", "User not found");
        }
    }

    private static String trimToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
