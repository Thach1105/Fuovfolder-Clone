package com.fuoverflow.payment.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.payment.persistence.PointBalanceEntity;
import com.fuoverflow.payment.persistence.PointBalanceRepository;
import com.fuoverflow.payment.persistence.PointTransactionEntity;
import com.fuoverflow.payment.persistence.PointTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class PointService {
    private final PointBalanceRepository balanceRepo;
    private final PointTransactionRepository transactionRepo;

    public PointService(PointBalanceRepository balanceRepo, PointTransactionRepository transactionRepo) {
        this.balanceRepo = balanceRepo;
        this.transactionRepo = transactionRepo;
    }

    @Transactional(readOnly = true)
    public PointBalanceEntity getOrCreateBalance(UUID userId) {
        return balanceRepo.findByUserId(userId)
                .orElseGet(() -> balanceRepo.save(PointBalanceEntity.create(userId)));
    }

    @Transactional
    public void creditPoints(UUID userId, long points, String type, UUID paymentId, String description) {
        PointBalanceEntity balance = getOrCreateBalance(userId);
        balance.setBalancePoints(balance.getBalancePoints() + points);
        balanceRepo.save(balance);

        PointTransactionEntity txn = PointTransactionEntity.create(
                userId, points, "credit", type, "payment", paymentId, paymentId, description
        );
        transactionRepo.save(txn);
    }

    @Transactional
    public void debitPoints(UUID userId, long points, String type, String description) {
        PointBalanceEntity balance = balanceRepo.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("BALANCE_NOT_FOUND", "Point balance not found"));

        if (balance.getBalancePoints() < points) {
            throw new IllegalStateException("Insufficient points");
        }

        balance.setBalancePoints(balance.getBalancePoints() - points);
        balanceRepo.save(balance);

        PointTransactionEntity txn = PointTransactionEntity.create(
                userId, points, "debit", type, null, null, null, description
        );
        transactionRepo.save(txn);
    }

    public java.util.List<PointTransactionEntity> getTransactions(UUID userId) {
        return transactionRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
