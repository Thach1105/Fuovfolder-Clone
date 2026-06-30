package com.fuoverflow.source.application;

import com.fuoverflow.award.application.PointsWalletService;
import com.fuoverflow.award.persistence.PointsLedgerEntity;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.voucher.VoucherRedemptionPort;
import com.fuoverflow.source.config.SourceProperties;
import com.fuoverflow.source.persistence.SourceCatalogItemEntity;
import com.fuoverflow.source.persistence.SourceCatalogItemRepository;
import com.fuoverflow.source.persistence.SourcePurchaseEntity;
import com.fuoverflow.source.persistence.SourcePurchaseEventRepository;
import com.fuoverflow.source.persistence.SourcePurchaseRepository;
import com.fuoverflow.user.persistence.UserEntity;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourcePurchaseServiceTest {
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
    @Mock
    private VoucherRedemptionPort voucherRedemptionPort;

    private SourcePurchaseService service;
    private UUID userId;
    private UUID catalogId;

    @BeforeEach
    void setUp() {
        service = new SourcePurchaseService(
                purchaseRepository,
                eventRepository,
                catalogRepository,
                userRepository,
                walletService,
                new SourceProperties(true, 24),
                voucherRedemptionPort);
        userId = UUID.randomUUID();
        catalogId = UUID.randomUUID();
    }

    @Test
    void purchase_debitsPointsAndPersists() {
        stubActiveUser();
        SourceCatalogItemEntity catalog = catalog(90000, 60);
        when(catalogRepository.findByIdAndDeletedAtIsNull(catalogId)).thenReturn(Optional.of(catalog));
        when(purchaseRepository.findFirstByUserIdAndCatalogItemIdAndStatusOrderByEndsAtDesc(userId, catalogId, "active"))
                .thenReturn(Optional.empty());
        when(walletService.debit(eq(userId), eq(90000), anyString(), eq(PointsWalletService.SOURCE_SOURCE_PURCHASE), any(UUID.class)))
                .thenReturn(PointsLedgerEntity.entry(UUID.randomUUID(), userId, -90000, "x", "source_purchase", UUID.randomUUID(), Instant.now()));
        when(purchaseRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.purchase(userId, catalogId, null, null);

        assertEquals("active", response.status());
        assertTrue(response.active());
        verify(walletService).debit(eq(userId), eq(90000), anyString(), eq(PointsWalletService.SOURCE_SOURCE_PURCHASE), any(UUID.class));
        verify(purchaseRepository).saveAndFlush(any(SourcePurchaseEntity.class));
        verify(eventRepository).save(any());
    }

    @Test
    void purchase_failsWhenInsufficientBalance() {
        stubActiveUser();
        when(catalogRepository.findByIdAndDeletedAtIsNull(catalogId)).thenReturn(Optional.of(catalog(90000, 60)));
        when(purchaseRepository.findFirstByUserIdAndCatalogItemIdAndStatusOrderByEndsAtDesc(userId, catalogId, "active"))
                .thenReturn(Optional.empty());
        when(walletService.debit(any(), anyInt(), anyString(), anyString(), any()))
                .thenThrow(new ConflictException("INSUFFICIENT_POINTS", "Insufficient Fuexam Point balance"));

        assertThrows(ConflictException.class, () -> service.purchase(userId, catalogId, null, null));
        verify(purchaseRepository, never()).saveAndFlush(any());
    }

    @Test
    void purchase_rejectsUnverifiedUser() {
        UserEntity user = UserEntity.pending(
                userId, "a@b.com", "a@b.com", "user", "user", "hash", "User", null, Instant.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(ForbiddenException.class, () -> service.purchase(userId, catalogId, null, null));
        verify(catalogRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void purchase_returnsExistingOnIdempotencyKey() {
        stubActiveUser();
        SourcePurchaseEntity existing = SourcePurchaseEntity.createActive(
                UUID.randomUUID(), userId, catalogId, "MLN111", "Triết", 90000, 60,
                Instant.now(), Instant.now().plus(60, ChronoUnit.DAYS), "key-1", Instant.now());
        when(purchaseRepository.findByUserIdAndIdempotencyKey(userId, "key-1")).thenReturn(Optional.of(existing));

        service.purchase(userId, catalogId, "key-1", null);

        verify(walletService, never()).debit(any(), anyInt(), anyString(), anyString(), any());
        verify(catalogRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void purchase_rejectsWhenActiveAccessRemains() {
        stubActiveUser();
        when(catalogRepository.findByIdAndDeletedAtIsNull(catalogId)).thenReturn(Optional.of(catalog(90000, 60)));
        SourcePurchaseEntity active = SourcePurchaseEntity.createActive(
                UUID.randomUUID(), userId, catalogId, "MLN111", "Triết", 90000, 60,
                Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS), null, Instant.now());
        when(purchaseRepository.findFirstByUserIdAndCatalogItemIdAndStatusOrderByEndsAtDesc(userId, catalogId, "active"))
                .thenReturn(Optional.of(active));

        ConflictException ex = assertThrows(ConflictException.class, () -> service.purchase(userId, catalogId, null, null));

        assertEquals("ACTIVE_ACCESS_REMAINS", ex.code());
        assertEquals("Source này vẫn còn thời gian sử dụng", ex.getMessage());
        verify(walletService, never()).debit(any(), anyInt(), anyString(), anyString(), any());
        verify(purchaseRepository, never()).saveAndFlush(any());
    }

    private SourceCatalogItemEntity catalog(int price, int accessDays) {
        return SourceCatalogItemEntity.create(
                catalogId, "MLN111", "Triết học Mác - Lênin", null, price, accessDays,
                583, 15472, 0, null, null, "on-thi", true, true, 10, Instant.now());
    }

    private void stubActiveUser() {
        UserEntity user = UserEntity.seededAdministrator(
                userId, "a@b.com", "a@b.com", "user", "user", "hash", "User", Instant.now());
        user.markEmailVerified(Instant.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }
}
