package com.fuoverflow.source.application;

import com.fuoverflow.award.application.PointsWalletService;
import com.fuoverflow.award.persistence.PointsLedgerEntity;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.source.api.dto.AdminPurchasePageResponse;
import com.fuoverflow.source.api.dto.AdminPurchaseResponse;
import com.fuoverflow.source.api.dto.SourceOverviewResponse;
import com.fuoverflow.source.config.SourceProperties;
import com.fuoverflow.source.domain.PurchaseStatus;
import com.fuoverflow.source.persistence.SourceCatalogItemRepository;
import com.fuoverflow.source.persistence.SourcePurchaseEntity;
import com.fuoverflow.source.persistence.SourcePurchaseEventEntity;
import com.fuoverflow.source.persistence.SourcePurchaseEventRepository;
import com.fuoverflow.source.persistence.SourcePurchaseRepository;
import com.fuoverflow.source.persistence.SourcePurchaseSpecifications;
import com.fuoverflow.user.persistence.UserEntity;
import com.fuoverflow.user.persistence.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SourcePurchaseAdminService {
    private static final int MAX_PAGE_SIZE = 100;

    private final SourcePurchaseRepository purchaseRepository;
    private final SourcePurchaseEventRepository eventRepository;
    private final SourceCatalogItemRepository catalogRepository;
    private final UserRepository userRepository;
    private final PointsWalletService walletService;
    private final SourceProperties properties;

    public SourcePurchaseAdminService(
            SourcePurchaseRepository purchaseRepository,
            SourcePurchaseEventRepository eventRepository,
            SourceCatalogItemRepository catalogRepository,
            UserRepository userRepository,
            PointsWalletService walletService,
            SourceProperties properties) {
        this.purchaseRepository = purchaseRepository;
        this.eventRepository = eventRepository;
        this.catalogRepository = catalogRepository;
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public SourceOverviewResponse overview() {
        long total = purchaseRepository.count();
        long active = 0;
        long refunded = 0;
        for (Object[] row : purchaseRepository.countByStatusAll()) {
            String status = (String) row[0];
            long count = (Long) row[1];
            switch (PurchaseStatus.fromDb(status)) {
                case ACTIVE -> active = count;
                case REFUNDED -> refunded = count;
                default -> {
                    // expired/cancelled excluded from these counters
                }
            }
        }
        return new SourceOverviewResponse(
                total,
                active,
                refunded,
                catalogRepository.countActive(),
                purchaseRepository.sumPaidPoints(),
                purchaseRepository.sumRefundedPoints());
    }

    @Transactional(readOnly = true)
    public AdminPurchasePageResponse listAll(
            UUID userId, UUID catalogItemId, String status, String code, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        String dbStatus = status != null && !status.isBlank()
                ? PurchaseStatus.valueOf(status.trim().toUpperCase()).toDb()
                : null;
        Page<SourcePurchaseEntity> result = purchaseRepository.findAll(
                SourcePurchaseSpecifications.adminSearch(userId, catalogItemId, dbStatus, code, null, null),
                PageRequest.of(safePage, safeSize));
        Map<UUID, UserEntity> users = loadUsers(result.getContent());
        return new AdminPurchasePageResponse(
                result.getContent().stream().map(p -> toAdmin(p, users.get(p.getUserId()))).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminPurchaseResponse getDetail(UUID purchaseId) {
        SourcePurchaseEntity purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new NotFoundException("PURCHASE_NOT_FOUND", "Purchase not found"));
        UserEntity user = userRepository.findById(purchase.getUserId()).orElse(null);
        return toAdmin(purchase, user);
    }

    @Transactional
    public AdminPurchaseResponse refund(UUID actorId, UUID purchaseId, String reason) {
        if (!properties.refundEnabledOrDefault()) {
            throw new ForbiddenException("REFUND_DISABLED", "Refunds are disabled");
        }
        SourcePurchaseEntity purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new NotFoundException("PURCHASE_NOT_FOUND", "Purchase not found"));
        if (purchase.getRefundLedgerId() != null || "refunded".equals(purchase.getStatus())) {
            throw new ConflictException("ALREADY_REFUNDED", "Purchase already refunded");
        }
        if (!"active".equals(purchase.getStatus())) {
            throw new ConflictException("NOT_REFUNDABLE", "Only active purchases can be refunded");
        }
        if (purchase.getPaymentLedgerId() == null || purchase.getUnitPricePoints() <= 0) {
            throw new ConflictException("NOT_REFUNDABLE", "No payment recorded for this purchase");
        }

        Instant now = Instant.now();
        PointsLedgerEntity refundEntry = walletService.credit(
                purchase.getUserId(),
                purchase.getUnitPricePoints(),
                "Refund Suộc: " + purchase.getCodeSnapshot(),
                PointsWalletService.SOURCE_SOURCE_PURCHASE,
                purchase.getId());

        String fromStatus = purchase.getStatus();
        purchase.setStatus("refunded");
        purchase.setRefundLedgerId(refundEntry.getId());
        purchase.setRefundReason(reason != null && !reason.isBlank() ? reason.trim() : null);
        purchase.setEndsAt(now);
        purchase.setUpdatedAt(now);
        purchaseRepository.save(purchase);

        eventRepository.save(SourcePurchaseEventEntity.create(
                UUID.randomUUID(), purchase.getId(), "refund", fromStatus, "refunded", actorId,
                purchase.getRefundReason(), now));

        UserEntity user = userRepository.findById(purchase.getUserId()).orElse(null);
        return toAdmin(purchase, user);
    }

    private Map<UUID, UserEntity> loadUsers(List<SourcePurchaseEntity> purchases) {
        List<UUID> ids = purchases.stream().map(SourcePurchaseEntity::getUserId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    }

    private AdminPurchaseResponse toAdmin(SourcePurchaseEntity p, UserEntity user) {
        return new AdminPurchaseResponse(
                p.getId(),
                p.getUserId(),
                user != null ? user.getUsername() : null,
                user != null ? user.getDisplayName() : null,
                p.getCatalogItemId(),
                p.getCodeSnapshot(),
                p.getTitleSnapshot(),
                p.getStatus(),
                p.getUnitPricePoints(),
                p.getAccessDaysSnapshot(),
                p.getStartsAt(),
                p.getEndsAt(),
                p.getRefundLedgerId() != null,
                p.getPaymentLedgerId(),
                p.getRefundLedgerId(),
                p.getRefundReason(),
                p.getCreatedAt());
    }
}
