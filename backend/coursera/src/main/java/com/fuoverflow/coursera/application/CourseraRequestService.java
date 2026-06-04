package com.fuoverflow.coursera.application;

import com.fuoverflow.award.application.PointsWalletService;
import com.fuoverflow.award.persistence.PointsLedgerEntity;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.coursera.api.dto.CreateCourseraRequestBody;
import com.fuoverflow.coursera.api.dto.RequestDetailResponse;
import com.fuoverflow.coursera.api.dto.RequestPageResponse;
import com.fuoverflow.coursera.api.dto.RequestStatsResponse;
import com.fuoverflow.coursera.api.dto.RequestSummaryResponse;
import com.fuoverflow.coursera.domain.RequestStatus;
import com.fuoverflow.coursera.persistence.CourseraCatalogItemEntity;
import com.fuoverflow.coursera.persistence.CourseraCatalogItemRepository;
import com.fuoverflow.coursera.persistence.CourseraRequestCredentialEntity;
import com.fuoverflow.coursera.persistence.CourseraRequestCredentialRepository;
import com.fuoverflow.coursera.persistence.CourseraRequestItemEntity;
import com.fuoverflow.coursera.persistence.CourseraRequestItemRepository;
import com.fuoverflow.coursera.persistence.CourseraRequestStatusEventEntity;
import com.fuoverflow.coursera.persistence.CourseraRequestStatusEventRepository;
import com.fuoverflow.coursera.persistence.CourseraServiceRequestEntity;
import com.fuoverflow.coursera.persistence.CourseraServiceRequestRepository;
import com.fuoverflow.coursera.persistence.CourseraServiceRequestSpecifications;
import com.fuoverflow.coursera.support.CredentialEncryptionService;
import com.fuoverflow.user.domain.UserStatus;
import com.fuoverflow.user.persistence.UserEntity;
import com.fuoverflow.user.persistence.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CourseraRequestService {
    private final CourseraServiceRequestRepository requestRepository;
    private final CourseraRequestItemRepository itemRepository;
    private final CourseraRequestCredentialRepository credentialRepository;
    private final CourseraRequestStatusEventRepository eventRepository;
    private final CourseraCatalogItemRepository catalogRepository;
    private final UserRepository userRepository;
    private final PointsWalletService walletService;
    private final CredentialEncryptionService encryptionService;

    public CourseraRequestService(
            CourseraServiceRequestRepository requestRepository,
            CourseraRequestItemRepository itemRepository,
            CourseraRequestCredentialRepository credentialRepository,
            CourseraRequestStatusEventRepository eventRepository,
            CourseraCatalogItemRepository catalogRepository,
            UserRepository userRepository,
            PointsWalletService walletService,
            CredentialEncryptionService encryptionService) {
        this.requestRepository = requestRepository;
        this.itemRepository = itemRepository;
        this.credentialRepository = credentialRepository;
        this.eventRepository = eventRepository;
        this.catalogRepository = catalogRepository;
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.encryptionService = encryptionService;
    }

    @Transactional
    public RequestDetailResponse create(UUID userId, CreateCourseraRequestBody body, String idempotencyKey) {
        requireEligibleUser(userId);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = requestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey.trim());
            if (existing.isPresent()) {
                return toUserDetail(existing.get());
            }
        }

        CourseraCatalogItemEntity catalog = catalogRepository.findByIdAndDeletedAtIsNull(body.catalogItemId())
                .filter(CourseraCatalogItemEntity::isActive)
                .orElseThrow(() -> new NotFoundException("CATALOG_NOT_FOUND", "Catalog item not found or inactive"));

        int totalPoints = catalog.getPricePoints();
        Instant now = Instant.now();
        UUID requestId = UUID.randomUUID();

        PointsLedgerEntity payment = walletService.debit(
                userId,
                totalPoints,
                "Coursera service: " + catalog.getCode(),
                PointsWalletService.SOURCE_COURSERA_REQUEST,
                requestId);

        CourseraServiceRequestEntity request = CourseraServiceRequestEntity.createPending(
                requestId,
                userId,
                totalPoints,
                body.userNotes(),
                idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey.trim() : null,
                now);
        request.setPaymentLedgerId(payment.getId());
        requestRepository.save(request);

        itemRepository.save(CourseraRequestItemEntity.create(
                UUID.randomUUID(),
                requestId,
                catalog.getId(),
                catalog.getTitle(),
                catalog.getPricePoints(),
                1,
                now));

        String ciphertext = encryptionService.encrypt(body.courseraPassword());
        credentialRepository.save(CourseraRequestCredentialEntity.create(
                UUID.randomUUID(),
                requestId,
                body.courseraEmail().trim(),
                ciphertext,
                encryptionService.keyId(),
                now));

        eventRepository.save(CourseraRequestStatusEventEntity.create(
                UUID.randomUUID(),
                requestId,
                null,
                RequestStatus.PENDING.toDb(),
                userId,
                "Request created",
                now));

        return toUserDetail(request);
    }

    @Transactional(readOnly = true)
    public RequestPageResponse listMine(UUID userId, String status, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<CourseraServiceRequestEntity> result;
        if (status != null && !status.isBlank()) {
            String dbStatus = RequestStatus.valueOf(status.trim().toUpperCase()).toDb();
            result = requestRepository.findAll(
                    CourseraServiceRequestSpecifications.adminSearch(userId, null, dbStatus, null, null),
                    PageRequest.of(page, safeSize));
        } else {
            result = requestRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, safeSize));
        }
        List<CourseraServiceRequestEntity> content = result.getContent();
        var batch = CourseraRequestSummaryAssembler.loadBatch(
                content, itemRepository, catalogRepository, userRepository, false);
        return new RequestPageResponse(
                content.stream().map(r -> CourseraRequestSummaryAssembler.toSummary(r, batch)).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public RequestStatsResponse stats(UUID userId) {
        long total = requestRepository.countByUserId(userId);
        long pending = 0;
        long inProgress = 0;
        long completed = 0;
        long cancelled = 0;
        for (Object[] row : requestRepository.countByStatusForUser(userId)) {
            String st = (String) row[0];
            long count = (Long) row[1];
            switch (RequestStatus.fromDb(st)) {
                case PENDING -> pending = count;
                case IN_PROGRESS -> inProgress = count;
                case COMPLETED -> completed = count;
                case CANCELLED -> cancelled = count;
            }
        }
        return new RequestStatsResponse(total, pending, inProgress, completed, cancelled);
    }

    @Transactional(readOnly = true)
    public RequestDetailResponse getMine(UUID userId, UUID requestId) {
        CourseraServiceRequestEntity request = requestRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new NotFoundException("REQUEST_NOT_FOUND", "Request not found"));
        return toUserDetail(request);
    }

    private void requireEligibleUser(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new BadRequestException("USER_NOT_FOUND", "User not found"));
        if (user.getStatus() != UserStatus.ACTIVE || user.getEmailVerifiedAt() == null) {
            throw new ForbiddenException("USER_NOT_ELIGIBLE", "Account must be active with verified email");
        }
    }

    private RequestDetailResponse toUserDetail(CourseraServiceRequestEntity request) {
        List<CourseraRequestItemEntity> items = itemRepository.findByRequestId(request.getId());
        var lines = items.stream()
                .map(i -> new RequestDetailResponse.RequestItemLineResponse(
                        i.getCatalogItemId(),
                        i.getItemTitleSnapshot(),
                        i.getUnitPricePoints(),
                        i.getQuantity()))
                .toList();
        String email = credentialRepository.findByRequestId(request.getId())
                .map(CourseraRequestCredentialEntity::getCourseraEmail)
                .orElse("");
        return new RequestDetailResponse(
                request.getId(),
                request.getStatus(),
                request.getTotalPoints(),
                request.getUserNotes(),
                email,
                lines,
                request.getCreatedAt(),
                request.getStatusChangedAt());
    }
}
