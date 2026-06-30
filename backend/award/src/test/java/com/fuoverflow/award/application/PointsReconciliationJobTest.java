package com.fuoverflow.award.application;

import com.fuoverflow.award.persistence.PointBalanceEntity;
import com.fuoverflow.award.persistence.PointBalanceRepository;
import com.fuoverflow.award.persistence.PointsLedgerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointsReconciliationJobTest {

    @Mock private PointBalanceRepository balanceRepo;
    @Mock private PointsLedgerRepository ledgerRepo;

    @InjectMocks
    private PointsReconciliationJob job;

    @Test
    void shouldAutoCorrectDriftedBalance() {
        UUID userId = UUID.randomUUID();
        PointBalanceEntity balance = PointBalanceEntity.create(userId);
        balance.adjustBalance(500); // cached says 500

        when(balanceRepo.findAll()).thenReturn(List.of(balance));
        when(ledgerRepo.sumDeltaByUserId(userId)).thenReturn(800L); // ledger says 800

        job.reconcile();

        verify(balanceRepo).save(balance);
        // balance should now be 800
        assert balance.getBalancePoints() == 800;
    }

    @Test
    void shouldNotTouchCorrectBalance() {
        UUID userId = UUID.randomUUID();
        PointBalanceEntity balance = PointBalanceEntity.create(userId);
        balance.adjustBalance(500);

        when(balanceRepo.findAll()).thenReturn(List.of(balance));
        when(ledgerRepo.sumDeltaByUserId(userId)).thenReturn(500L);

        job.reconcile();

        verify(balanceRepo, never()).save(any());
    }
}
