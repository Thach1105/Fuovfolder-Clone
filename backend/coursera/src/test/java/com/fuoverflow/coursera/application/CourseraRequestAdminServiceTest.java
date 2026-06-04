package com.fuoverflow.coursera.application;

import com.fuoverflow.award.application.PointsWalletService;
import com.fuoverflow.award.persistence.PointsLedgerEntity;
import com.fuoverflow.coursera.api.dto.UpdateRequestStatusBody;
import com.fuoverflow.coursera.config.CourseraProperties;
import com.fuoverflow.coursera.persistence.CourseraCatalogItemRepository;
import com.fuoverflow.coursera.persistence.CourseraRequestCredentialEntity;
import com.fuoverflow.coursera.persistence.CourseraRequestCredentialRepository;
import com.fuoverflow.coursera.persistence.CourseraRequestItemRepository;
import com.fuoverflow.coursera.persistence.CourseraRequestStatusEventRepository;
import com.fuoverflow.coursera.persistence.CourseraServiceRequestEntity;
import com.fuoverflow.coursera.persistence.CourseraServiceRequestRepository;
import com.fuoverflow.coursera.support.CredentialEncryptionService;
import com.fuoverflow.user.persistence.UserEntity;
import com.fuoverflow.user.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseraRequestAdminServiceTest {
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
    private CourseraCatalogAdminService catalogAdminService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PointsWalletService walletService;
    @Mock
    private CredentialEncryptionService encryptionService;

    private CourseraRequestAdminService service;
    private UUID requestId;
    private UUID userId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        CourseraProperties properties = new CourseraProperties(
                new CourseraProperties.CredentialsEncryption("test-key-32-characters-long!!!!", "default"),
                true);
        service = new CourseraRequestAdminService(
                requestRepository,
                itemRepository,
                credentialRepository,
                eventRepository,
                catalogRepository,
                catalogAdminService,
                userRepository,
                walletService,
                encryptionService,
                properties);
        requestId = UUID.randomUUID();
        userId = UUID.randomUUID();
        actorId = UUID.randomUUID();
    }

    @Test
    void cancelFromPendingRefundsPoints() {
        CourseraServiceRequestEntity request = pendingRequestWithPayment(50000);
        stubDetailLoad(request);

        service.updateStatus(actorId, requestId, new UpdateRequestStatusBody("cancelled", "refund", null));

        verify(walletService).credit(eq(userId), eq(50000), anyString(), anyString(), eq(requestId));
        verify(eventRepository).save(any());
    }

    @Test
    void cancelFromInProgressRefundsPoints() {
        CourseraServiceRequestEntity request = pendingRequestWithPayment(120000);
        request.setStatus("in_progress");
        stubDetailLoad(request);

        service.updateStatus(actorId, requestId, new UpdateRequestStatusBody("cancelled", "refund", null));

        verify(walletService).credit(eq(userId), eq(120000), anyString(), anyString(), eq(requestId));
        verify(eventRepository).save(any());
    }

    private CourseraServiceRequestEntity pendingRequestWithPayment(int totalPoints) {
        CourseraServiceRequestEntity request = CourseraServiceRequestEntity.createPending(
                requestId, userId, totalPoints, null, null, Instant.now());
        request.setPaymentLedgerId(UUID.randomUUID());
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(walletService.credit(eq(userId), eq(totalPoints), anyString(), anyString(), eq(requestId)))
                .thenReturn(PointsLedgerEntity.entry(
                        UUID.randomUUID(), userId, totalPoints, "refund", "coursera_request", requestId, Instant.now()));
        return request;
    }

    private void stubDetailLoad(CourseraServiceRequestEntity request) {
        UserEntity user = UserEntity.seededAdministrator(userId, "a@b.com", "a@b.com", "u", "u", "h", "U", Instant.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(itemRepository.findByRequestId(requestId)).thenReturn(List.of());
        when(credentialRepository.findByRequestId(requestId)).thenReturn(Optional.of(
                CourseraRequestCredentialEntity.create(UUID.randomUUID(), requestId, "e@mail.com", "c", "default", Instant.now())));
        when(encryptionService.decrypt("c")).thenReturn("plain");
    }
}
