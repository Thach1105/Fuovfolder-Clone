package com.fuoverflow.source.application;

import com.fuoverflow.source.persistence.SourcePurchaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Flips active purchases whose window elapsed to {@code expired} so stats and
 * access checks stay accurate without scanning {@code ends_at} on every request.
 */
@Service
public class SourcePurchaseExpiryService {
    private static final Logger log = LoggerFactory.getLogger(SourcePurchaseExpiryService.class);

    private final SourcePurchaseRepository purchaseRepository;

    public SourcePurchaseExpiryService(SourcePurchaseRepository purchaseRepository) {
        this.purchaseRepository = purchaseRepository;
    }

    @Scheduled(fixedDelayString = "${fuoverflow.source.expiry-scan-interval-ms:60000}")
    @Transactional
    public void expireElapsedPurchases() {
        int updated = purchaseRepository.markExpired(Instant.now());
        if (updated > 0) {
            log.info("Marked {} source purchase(s) as expired", updated);
        }
    }
}
