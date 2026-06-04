package com.fuoverflow.source.application;

import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.source.persistence.SourcePurchaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Central access check for Source content. Phase 1 exposes only the marketplace,
 * but content endpoints (question bank, flashcards) will gate on an active purchase.
 */
@Service
public class SourceAccessGuard {
    private final SourcePurchaseRepository purchaseRepository;

    public SourceAccessGuard(SourcePurchaseRepository purchaseRepository) {
        this.purchaseRepository = purchaseRepository;
    }

    @Transactional(readOnly = true)
    public boolean hasActiveAccess(UUID userId, UUID catalogItemId) {
        return purchaseRepository.existsByUserIdAndCatalogItemIdAndStatusAndEndsAtAfter(
                userId, catalogItemId, "active", Instant.now());
    }

    @Transactional(readOnly = true)
    public void requireActiveAccess(UUID userId, UUID catalogItemId) {
        if (!hasActiveAccess(userId, catalogItemId)) {
            throw new ForbiddenException("NO_ACTIVE_ACCESS", "You do not have active access to this material");
        }
    }
}
