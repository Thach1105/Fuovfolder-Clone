package com.fuoverflow.coursera.application;

import com.fuoverflow.award.application.PointsWalletService;
import com.fuoverflow.award.persistence.PointsLedgerEntity;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.coursera.api.dto.CreateCourseraRequestBody;
import com.fuoverflow.coursera.persistence.CourseraCatalogItemEntity;
import com.fuoverflow.coursera.persistence.CourseraCatalogItemRepository;
import com.fuoverflow.coursera.persistence.CourseraRequestCredentialRepository;
import com.fuoverflow.coursera.persistence.CourseraRequestItemRepository;
import com.fuoverflow.coursera.persistence.CourseraRequestStatusEventRepository;
import com.fuoverflow.coursera.persistence.CourseraServiceRequestEntity;
import com.fuoverflow.coursera.persistence.CourseraServiceRequestRepository;
import com.fuoverflow.coursera.support.CredentialEncryptionService;
import com.fuoverflow.user.domain.UserStatus;
import com.fuoverflow.user.persistence.UserEntity;
import com.fuoverflow.user.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseraRequestServiceTest {
    @Mock
    private CourseraServiceRequestRepository requestRepository;
    @Mock
    private CourseraRequestItemRepository itemRepository;
    @Mock
    private CourseraRequestCredentialRepository credentialRepository;
    @Mock
    private CourseraRequestStatusEventRepository eventRepository;
    @Mock
    private CourseraCatalogItemRepository catalogRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PointsWalletService walletService;
    @Mock
    private CredentialEncryptionService encryptionService;

    private CourseraRequestService service;
    private UUID userId;
    private UUID catalogId;

    @BeforeEach
    void setUp() {
        service = new CourseraRequestService(
                requestRepository,
                itemRepository,
                credentialRepository,
                eventRepository,
                catalogRepository,
                userRepository,
                walletService,
                encryptionService);
        userId = UUID.randomUUID();
        catalogId = UUID.randomUUID();
    }

    @Test
    void create_debitsPointsAndPersistsRequest() {
        stubActiveUser();
        CourseraCatalogItemEntity catalog = CourseraCatalogItemEntity.create(
                catalogId, "WOU203C", "UX Course", null, 250000, true, true, 1, Instant.now());
        when(catalogRepository.findByIdAndDeletedAtIsNull(catalogId)).thenReturn(Optional.of(catalog));
        UUID ledgerId = UUID.randomUUID();
        when(walletService.debit(eq(userId), eq(250000), anyString(), anyString(), any(UUID.class)))
                .thenReturn(PointsLedgerEntity.entry(ledgerId, userId, -250000, "x", "coursera_request", UUID.randomUUID(), Instant.now()));
        when(encryptionService.encrypt("secret")).thenReturn("cipher");
        when(encryptionService.keyId()).thenReturn("default");
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var body = new CreateCourseraRequestBody(catalogId, "user@coursera.org", "secret", "note");
        service.create(userId, body, null);

        verify(walletService).debit(eq(userId), eq(250000), anyString(), eq(PointsWalletService.SOURCE_COURSERA_REQUEST), any(UUID.class));
        verify(requestRepository).save(any(CourseraServiceRequestEntity.class));
        verify(credentialRepository).save(any());
    }

    @Test
    void create_skipsDebitForFreeCatalogItem() {
        stubActiveUser();
        CourseraCatalogItemEntity catalog = CourseraCatalogItemEntity.create(
                catalogId, "FREE-1", "Free Course", null, 0, true, true, 1, Instant.now());
        when(catalogRepository.findByIdAndDeletedAtIsNull(catalogId)).thenReturn(Optional.of(catalog));
        when(encryptionService.encrypt("secret")).thenReturn("cipher");
        when(encryptionService.keyId()).thenReturn("default");
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var body = new CreateCourseraRequestBody(catalogId, "user@coursera.org", "secret", "note");
        service.create(userId, body, null);

        verify(walletService, never()).debit(any(), anyInt(), anyString(), anyString(), any());
        ArgumentCaptor<CourseraServiceRequestEntity> requestCaptor = ArgumentCaptor.forClass(CourseraServiceRequestEntity.class);
        verify(requestRepository).save(requestCaptor.capture());
        CourseraServiceRequestEntity saved = requestCaptor.getValue();
        assertEquals(0, saved.getTotalPoints());
        assertNull(saved.getPaymentLedgerId());
        verify(itemRepository).save(any());
        verify(credentialRepository).save(any());
        verify(eventRepository).save(any());
    }

    @Test
    void create_failsWhenInsufficientBalance() {
        stubActiveUser();
        CourseraCatalogItemEntity catalog = CourseraCatalogItemEntity.create(
                catalogId, "WOU203C", "UX Course", null, 250000, true, true, 1, Instant.now());
        when(catalogRepository.findByIdAndDeletedAtIsNull(catalogId)).thenReturn(Optional.of(catalog));
        when(walletService.debit(any(), anyInt(), anyString(), anyString(), any()))
                .thenThrow(new ConflictException("INSUFFICIENT_POINTS", "Insufficient Fuexam Point balance"));

        var body = new CreateCourseraRequestBody(catalogId, "user@coursera.org", "secret", null);
        assertThrows(ConflictException.class, () -> service.create(userId, body, null));
        verify(requestRepository, never()).save(any());
    }

    @Test
    void create_rejectsUnverifiedUser() {
        UserEntity user = UserEntity.pending(
                userId, "a@b.com", "a@b.com", "user", "user", "hash", "User", null, Instant.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        var body = new CreateCourseraRequestBody(catalogId, "user@coursera.org", "secret", null);
        assertThrows(ForbiddenException.class, () -> service.create(userId, body, null));
    }

    @Test
    void create_returnsExistingOnIdempotencyKey() {
        stubActiveUser();
        UUID requestId = UUID.randomUUID();
        CourseraServiceRequestEntity existing = CourseraServiceRequestEntity.createPending(
                requestId, userId, 100, null, "key-1", Instant.now());
        when(requestRepository.findByUserIdAndIdempotencyKey(userId, "key-1")).thenReturn(Optional.of(existing));
        when(itemRepository.findByRequestId(requestId)).thenReturn(java.util.List.of());
        when(credentialRepository.findByRequestId(requestId)).thenReturn(Optional.empty());

        var body = new CreateCourseraRequestBody(catalogId, "user@coursera.org", "secret", null);
        service.create(userId, body, "key-1");

        verify(walletService, never()).debit(any(), anyInt(), anyString(), anyString(), any());
        verify(catalogRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    private void stubActiveUser() {
        UserEntity user = UserEntity.seededAdministrator(
                userId, "a@b.com", "a@b.com", "user", "user", "hash", "User", Instant.now());
        user.markEmailVerified(Instant.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }
}
