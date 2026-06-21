package com.fuoverflow.payment.application;

import com.fuoverflow.payment.persistence.PointBalanceEntity;
import com.fuoverflow.payment.persistence.PointBalanceRepository;
import com.fuoverflow.payment.persistence.PointTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private PointBalanceRepository balanceRepo;

    @Mock
    private PointTransactionRepository transactionRepo;

    @InjectMocks
    private PointService pointService;

    @Test
    void getOrCreateBalance_shouldReturnExistingBalance() {
        UUID userId = UUID.randomUUID();
        PointBalanceEntity balance = PointBalanceEntity.create(userId);
        when(balanceRepo.findByUserId(userId)).thenReturn(Optional.of(balance));

        PointBalanceEntity result = pointService.getOrCreateBalance(userId);

        assertThat(result).isSameAs(balance);
        verify(balanceRepo, times(1)).findByUserId(userId);
        verify(balanceRepo, times(0)).save(any(PointBalanceEntity.class));
    }

    @Test
    void getOrCreateBalance_shouldRetryFetchWhenConcurrentInsertHappens() {
        UUID userId = UUID.randomUUID();
        PointBalanceEntity balance = PointBalanceEntity.create(userId);

        when(balanceRepo.findByUserId(userId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(balance));
        when(balanceRepo.save(any(PointBalanceEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        PointBalanceEntity result = pointService.getOrCreateBalance(userId);

        assertThat(result).isSameAs(balance);
        verify(balanceRepo, times(2)).findByUserId(userId);
        verify(balanceRepo, times(1)).save(any(PointBalanceEntity.class));
    }

    @Test
    void getOrCreateBalance_shouldThrowWhenRetryFetchStillMissing() {
        UUID userId = UUID.randomUUID();

        when(balanceRepo.findByUserId(userId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(balanceRepo.save(any(PointBalanceEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> pointService.getOrCreateBalance(userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to get or create balance");
    }
}
