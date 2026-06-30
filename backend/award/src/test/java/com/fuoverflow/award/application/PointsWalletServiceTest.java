package com.fuoverflow.award.application;

import com.fuoverflow.award.persistence.PointBalanceEntity;
import com.fuoverflow.award.persistence.PointBalanceRepository;
import com.fuoverflow.award.persistence.PointsLedgerEntity;
import com.fuoverflow.award.persistence.PointsLedgerRepository;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.user.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointsWalletServiceTest {

    @Mock private PointsLedgerRepository ledgerRepo;
    @Mock private PointBalanceRepository balanceRepo;
    @Mock private UserRepository userRepo;

    private PointsWalletService service;

    @BeforeEach
    void setUp() {
        service = new PointsWalletService(ledgerRepo, balanceRepo, userRepo);
    }

    @Test
    void credit_shouldInsertLedgerAndUpdateBalance() {
        UUID userId = UUID.randomUUID();
        when(userRepo.existsByIdAndDeletedAtIsNull(userId)).thenReturn(true);

        PointBalanceEntity balance = PointBalanceEntity.create(userId);
        when(balanceRepo.findByUserId(userId)).thenReturn(Optional.of(balance));
        when(ledgerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.credit(userId, 500, "test", "manual", UUID.randomUUID());

        assertThat(balance.getBalancePoints()).isEqualTo(500);
        verify(balanceRepo).save(balance);
        verify(ledgerRepo).save(any(PointsLedgerEntity.class));
    }

    @Test
    void debit_shouldInsertLedgerAndReduceBalance() {
        UUID userId = UUID.randomUUID();
        when(userRepo.existsByIdAndDeletedAtIsNull(userId)).thenReturn(true);

        PointBalanceEntity balance = PointBalanceEntity.create(userId);
        balance.adjustBalance(1000);
        when(balanceRepo.findByUserId(userId)).thenReturn(Optional.of(balance));
        when(ledgerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.debit(userId, 300, "purchase", "membership", UUID.randomUUID());

        assertThat(balance.getBalancePoints()).isEqualTo(700);
        verify(balanceRepo).save(balance);
    }

    @Test
    void debit_shouldRejectInsufficientBalance() {
        UUID userId = UUID.randomUUID();
        when(userRepo.existsByIdAndDeletedAtIsNull(userId)).thenReturn(true);

        PointBalanceEntity balance = PointBalanceEntity.create(userId);
        balance.adjustBalance(100);
        when(balanceRepo.findByUserId(userId)).thenReturn(Optional.of(balance));

        assertThatThrownBy(() ->
                service.debit(userId, 500, "purchase", "membership", UUID.randomUUID())
        ).isInstanceOf(ConflictException.class)
                .hasMessageContaining("Insufficient");
    }

    @Test
    void adjust_positiveCreatesBalanceIfNotExists() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(userRepo.existsByIdAndDeletedAtIsNull(userId)).thenReturn(true);
        when(balanceRepo.findByUserId(userId)).thenReturn(Optional.empty());
        when(balanceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.adjust(userId, 200, "bonus", actorId);

        ArgumentCaptor<PointBalanceEntity> captor = ArgumentCaptor.forClass(PointBalanceEntity.class);
        verify(balanceRepo, atLeastOnce()).save(captor.capture());
        // The last save should have the adjusted balance
        PointBalanceEntity saved = captor.getAllValues().stream()
                .filter(b -> b.getBalancePoints() == 200)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected save with balance 200"));
        assertThat(saved.getBalancePoints()).isEqualTo(200);
    }

    @Test
    void getBalance_readsFromCachedBalance() {
        UUID userId = UUID.randomUUID();
        when(userRepo.existsByIdAndDeletedAtIsNull(userId)).thenReturn(true);

        PointBalanceEntity balance = PointBalanceEntity.create(userId);
        balance.adjustBalance(750);
        when(balanceRepo.findByUserId(userId)).thenReturn(Optional.of(balance));

        long result = service.getBalance(userId);

        assertThat(result).isEqualTo(750);
        verify(ledgerRepo, never()).sumDeltaByUserId(any());
    }

    @Test
    void getBalance_returnsZeroWhenNoBalanceRecord() {
        UUID userId = UUID.randomUUID();
        when(userRepo.existsByIdAndDeletedAtIsNull(userId)).thenReturn(true);
        when(balanceRepo.findByUserId(userId)).thenReturn(Optional.empty());

        long result = service.getBalance(userId);

        assertThat(result).isZero();
    }

    @Test
    void credit_shouldThrowWhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepo.existsByIdAndDeletedAtIsNull(userId)).thenReturn(false);

        assertThatThrownBy(() ->
                service.credit(userId, 100, "bonus", "admin", UUID.randomUUID())
        ).isInstanceOf(BadRequestException.class);
    }

    @Test
    void adjust_negativeWithInsufficientBalanceShouldFail() {
        UUID userId = UUID.randomUUID();
        when(userRepo.existsByIdAndDeletedAtIsNull(userId)).thenReturn(true);

        PointBalanceEntity balance = PointBalanceEntity.create(userId);
        balance.adjustBalance(50);
        when(balanceRepo.findByUserId(userId)).thenReturn(Optional.of(balance));

        assertThatThrownBy(() ->
                service.adjust(userId, -100, "penalty", UUID.randomUUID())
        ).isInstanceOf(ConflictException.class)
                .hasMessageContaining("negative balance");
    }
}
