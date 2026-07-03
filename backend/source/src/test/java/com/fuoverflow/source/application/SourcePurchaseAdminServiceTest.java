package com.fuoverflow.source.application;

import com.fuoverflow.award.application.PointsWalletService;
import com.fuoverflow.award.persistence.PointsLedgerEntity;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.source.config.SourceProperties;
import com.fuoverflow.source.persistence.SourceCatalogItemRepository;
import com.fuoverflow.source.persistence.SourcePurchaseEntity;
import com.fuoverflow.source.persistence.SourcePurchaseEventRepository;
import com.fuoverflow.source.persistence.SourcePurchaseRepository;
import com.fuoverflow.user.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourcePurchaseAdminServiceTest {
    @Mock
    private SourcePurchaseRepository purchaseRepository;
    @Mock
    private SourcePurchaseEventRepository eventRepository;
    @Mock
    private SourceCatalogItemRepository catalogRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PointsWalletService walletService;

    private SourcePurchaseAdminService service;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        service = new SourcePurchaseAdminService(
                purchaseRepository,
                eventRepository,
                catalogRepository,
                userRepository,
                walletService,
                new SourceProperties(true, 24, null, null));
        actorId = UUID.randomUUID();
    }

    @Test
    void refund_creditsPointsAndMarksRefunded() {
        UUID purchaseId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        SourcePurchaseEntity purchase = activePurchase(purchaseId, buyerId);
        when(purchaseRepository.findById(purchaseId)).thenReturn(Optional.of(purchase));
        when(walletService.credit(eq(buyerId), eq(90000), anyString(), eq(PointsWalletService.SOURCE_SOURCE_PURCHASE), eq(purchaseId)))
                .thenReturn(PointsLedgerEntity.entry(UUID.randomUUID(), buyerId, 90000, "x", "source_purchase", purchaseId, Instant.now()));
        when(userRepository.findById(buyerId)).thenReturn(Optional.empty());

        var response = service.refund(actorId, purchaseId, "duplicate");

        assertEquals("refunded", response.status());
        verify(walletService).credit(eq(buyerId), eq(90000), anyString(), eq(PointsWalletService.SOURCE_SOURCE_PURCHASE), eq(purchaseId));
        verify(purchaseRepository).save(purchase);
        verify(eventRepository).save(any());
    }

    @Test
    void refund_failsWhenAlreadyRefunded() {
        UUID purchaseId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        SourcePurchaseEntity purchase = activePurchase(purchaseId, buyerId);
        purchase.setRefundLedgerId(UUID.randomUUID());
        purchase.setStatus("refunded");
        when(purchaseRepository.findById(purchaseId)).thenReturn(Optional.of(purchase));

        assertThrows(ConflictException.class, () -> service.refund(actorId, purchaseId, null));
        verify(walletService, never()).credit(any(), anyInt(), anyString(), anyString(), any());
    }

    private SourcePurchaseEntity activePurchase(UUID purchaseId, UUID buyerId) {
        SourcePurchaseEntity purchase = SourcePurchaseEntity.createActive(
                purchaseId, buyerId, UUID.randomUUID(), "MLN111", "Triết", 90000, 60,
                Instant.now(), Instant.now().plus(60, ChronoUnit.DAYS), null, Instant.now());
        purchase.setPaymentLedgerId(UUID.randomUUID());
        return purchase;
    }
}
