package com.fuoverflow.award.application;

import com.fuoverflow.award.persistence.PointBalanceEntity;
import com.fuoverflow.award.persistence.PointBalanceRepository;
import com.fuoverflow.award.persistence.PointsLedgerEntity;
import com.fuoverflow.award.persistence.PointsLedgerRepository;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.user.persistence.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PointsWalletService {
    public static final String SOURCE_ADMIN_ADJUST = "admin_adjust";
    public static final String SOURCE_COURSERA_REQUEST = "coursera_request";
    public static final String SOURCE_SOURCE_PURCHASE = "source_purchase";
    public static final String SOURCE_CHECK_SCORE = "check_score";
    public static final String SOURCE_TOPUP = "topup";

    private final PointsLedgerRepository ledgerRepository;
    private final PointBalanceRepository balanceRepository;
    private final UserRepository userRepository;

    public PointsWalletService(PointsLedgerRepository ledgerRepository,
                               PointBalanceRepository balanceRepository,
                               UserRepository userRepository) {
        this.ledgerRepository = ledgerRepository;
        this.balanceRepository = balanceRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public long getBalance(UUID userId) {
        requireUserExists(userId);
        return balanceRepository.findByUserId(userId)
                .map(PointBalanceEntity::getBalancePoints)
                .orElse(0L);
    }

    @Transactional
    public PointsLedgerEntity debit(UUID userId, int amount, String reason, String sourceType, UUID sourceId) {
        if (amount <= 0) {
            throw new BadRequestException("INVALID_AMOUNT", "Debit amount must be positive");
        }
        requireUserExists(userId);
        PointBalanceEntity balance = getOrCreateBalance(userId);
        if (balance.getBalancePoints() < amount) {
            throw new ConflictException("INSUFFICIENT_POINTS", "Insufficient Fuexam Point balance");
        }
        balance.adjustBalance(-amount);
        balanceRepository.save(balance);
        return persistLedger(userId, -amount, reason, sourceType, sourceId);
    }

    @Transactional
    public PointsLedgerEntity credit(UUID userId, int amount, String reason, String sourceType, UUID sourceId) {
        if (amount <= 0) {
            throw new BadRequestException("INVALID_AMOUNT", "Credit amount must be positive");
        }
        requireUserExists(userId);
        PointBalanceEntity balance = getOrCreateBalance(userId);
        balance.adjustBalance(amount);
        balanceRepository.save(balance);
        return persistLedger(userId, amount, reason, sourceType, sourceId);
    }

    @Transactional
    public PointsLedgerEntity adjust(UUID userId, int delta, String reason, UUID actorUserId) {
        if (delta == 0) {
            throw new BadRequestException("INVALID_AMOUNT", "Adjustment delta cannot be zero");
        }
        requireUserExists(userId);
        PointBalanceEntity balance = getOrCreateBalance(userId);
        if (delta < 0 && balance.getBalancePoints() + delta < 0) {
            throw new ConflictException("INSUFFICIENT_POINTS", "Adjustment would result in negative balance");
        }
        balance.adjustBalance(delta);
        balanceRepository.save(balance);
        String fullReason = reason != null && !reason.isBlank() ? reason : "Admin adjustment";
        return persistLedger(userId, delta, fullReason, SOURCE_ADMIN_ADJUST, actorUserId);
    }

    private PointBalanceEntity getOrCreateBalance(UUID userId) {
        return balanceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    try {
                        return balanceRepository.save(PointBalanceEntity.create(userId));
                    } catch (DataIntegrityViolationException ex) {
                        return balanceRepository.findByUserId(userId)
                                .orElseThrow(() -> new IllegalStateException("Failed to create balance for user " + userId, ex));
                    }
                });
    }

    private PointsLedgerEntity persistLedger(UUID userId, int delta, String reason, String sourceType, UUID sourceId) {
        PointsLedgerEntity entry = PointsLedgerEntity.entry(
                UUID.randomUUID(),
                userId,
                delta,
                reason,
                sourceType,
                sourceId,
                Instant.now());
        return ledgerRepository.save(entry);
    }

    private void requireUserExists(UUID userId) {
        if (!userRepository.existsByIdAndDeletedAtIsNull(userId)) {
            throw new BadRequestException("USER_NOT_FOUND", "User not found");
        }
    }
}
