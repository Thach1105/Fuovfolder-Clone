package com.fuoverflow.award.application;

import com.fuoverflow.award.persistence.PointBalanceEntity;
import com.fuoverflow.award.persistence.PointBalanceRepository;
import com.fuoverflow.award.persistence.PointsLedgerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PointsReconciliationJob {
    private static final Logger log = LoggerFactory.getLogger(PointsReconciliationJob.class);

    private final PointBalanceRepository balanceRepository;
    private final PointsLedgerRepository ledgerRepository;

    public PointsReconciliationJob(PointBalanceRepository balanceRepository,
                                   PointsLedgerRepository ledgerRepository) {
        this.balanceRepository = balanceRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Scheduled(cron = "0 0 3 * * *") // 3 AM daily
    @Transactional
    public void reconcile() {
        log.info("Starting points reconciliation");
        int driftCount = 0;

        for (PointBalanceEntity balance : balanceRepository.findAll()) {
            long ledgerSum = ledgerRepository.sumDeltaByUserId(balance.getUserId());
            if (balance.getBalancePoints() != ledgerSum) {
                log.warn("Points drift detected: userId={}, cached={}, ledger={}",
                        balance.getUserId(), balance.getBalancePoints(), ledgerSum);
                balance.setBalancePoints(ledgerSum);
                balanceRepository.save(balance);
                driftCount++;
            }
        }

        log.info("Points reconciliation complete: {} drifted balances corrected", driftCount);
    }
}
