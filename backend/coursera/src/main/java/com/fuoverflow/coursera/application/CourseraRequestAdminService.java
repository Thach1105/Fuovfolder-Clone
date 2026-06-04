package com.fuoverflow.coursera.application;

import com.fuoverflow.award.application.PointsWalletService;
import com.fuoverflow.award.persistence.PointsLedgerEntity;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.coursera.api.dto.AdminRequestDetailResponse;
import com.fuoverflow.coursera.api.dto.CourseraOverviewResponse;
import com.fuoverflow.coursera.api.dto.RequestDetailResponse;
import com.fuoverflow.coursera.api.dto.RequestPageResponse;
import com.fuoverflow.coursera.api.dto.RequestStatsResponse;
import com.fuoverflow.coursera.api.dto.RequestSummaryResponse;
import com.fuoverflow.coursera.api.dto.UpdateRequestStatusBody;
import com.fuoverflow.coursera.config.CourseraProperties;
import com.fuoverflow.coursera.domain.RequestStatus;
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
import com.fuoverflow.user.persistence.UserEntity;
import com.fuoverflow.user.persistence.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class CourseraRequestAdminService {
    private final CourseraServiceRequestRepository requestRepository;
    private final CourseraRequestItemRepository itemRepository;
    private final CourseraRequestCredentialRepository credentialRepository;
    private final CourseraRequestStatusEventRepository eventRepository;
    private final CourseraCatalogItemRepository catalogRepository;
    private final CourseraCatalogAdminService catalogAdminService;
    private final UserRepository userRepository;
    private final PointsWalletService walletService;
    private final CredentialEncryptionService encryptionService;
    private final CourseraProperties properties;

    public CourseraRequestAdminService(
            CourseraServiceRequestRepository requestRepository,
            CourseraRequestItemRepository itemRepository,
            CourseraRequestCredentialRepository credentialRepository,
            CourseraRequestStatusEventRepository eventRepository,
            CourseraCatalogItemRepository catalogRepository,
            CourseraCatalogAdminService catalogAdminService,
            UserRepository userRepository,
            PointsWalletService walletService,
            CredentialEncryptionService encryptionService,
            CourseraProperties properties) {
        this.requestRepository = requestRepository;
        this.itemRepository = itemRepository;
        this.credentialRepository = credentialRepository;
        this.eventRepository = eventRepository;
        this.catalogRepository = catalogRepository;
        this.catalogAdminService = catalogAdminService;
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.encryptionService = encryptionService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public CourseraOverviewResponse overview() {
        RequestStatsResponse stats = buildStats(null);
        return new CourseraOverviewResponse(
                stats.total(),
                stats.pending(),
                stats.inProgress(),
                stats.completed(),
                stats.cancelled(),
                catalogAdminService.countActive());
    }

    @Transactional(readOnly = true)
    public RequestPageResponse listAll(
            UUID userId, UUID catalogItemId, String status, String period, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        String dbStatus = status != null && !status.isBlank()
                ? RequestStatus.valueOf(status.trim().toUpperCase()).toDb()
                : null;
        Instant from = resolveFrom(period);
        Instant to = resolveTo(period);
        Page<CourseraServiceRequestEntity> result = requestRepository.findAll(
                CourseraServiceRequestSpecifications.adminSearch(userId, catalogItemId, dbStatus, from, to),
                PageRequest.of(page, safeSize));
        List<CourseraServiceRequestEntity> content = result.getContent();
        var batch = CourseraRequestSummaryAssembler.loadBatch(
                content, itemRepository, catalogRepository, userRepository, true);
        return new RequestPageResponse(
                content.stream().map(r -> CourseraRequestSummaryAssembler.toSummary(r, batch)).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminRequestDetailResponse getDetail(UUID requestId) {
        CourseraServiceRequestEntity request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("REQUEST_NOT_FOUND", "Request not found"));
        UserEntity user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        List<CourseraRequestItemEntity> items = itemRepository.findByRequestId(requestId);
        var lines = items.stream()
                .map(i -> new RequestDetailResponse.RequestItemLineResponse(
                        i.getCatalogItemId(),
                        i.getItemTitleSnapshot(),
                        i.getUnitPricePoints(),
                        i.getQuantity()))
                .toList();
        CourseraRequestCredentialEntity cred = credentialRepository.findByRequestId(requestId)
                .orElseThrow(() -> new NotFoundException("CREDENTIAL_NOT_FOUND", "Credentials not found"));
        String password = encryptionService.decrypt(cred.getPasswordCiphertext());
        RequestStatus current = RequestStatus.fromDb(request.getStatus());
        return new AdminRequestDetailResponse(
                request.getId(),
                request.getUserId(),
                user.getUsername(),
                user.getDisplayName(),
                request.getStatus(),
                request.getTotalPoints(),
                request.getUserNotes(),
                cred.getCourseraEmail(),
                password,
                lines,
                request.getCreatedAt(),
                request.getStatusChangedAt(),
                request.getAssignedToUserId(),
                RequestStatusTransition.allowedNextDb(current),
                request.getRefundLedgerId() != null,
                request.getPaymentLedgerId(),
                request.getRefundLedgerId());
    }

    @Transactional
    public AdminRequestDetailResponse updateStatus(UUID actorId, UUID requestId, UpdateRequestStatusBody body) {
        CourseraServiceRequestEntity request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("REQUEST_NOT_FOUND", "Request not found"));
        RequestStatus from = RequestStatus.fromDb(request.getStatus());
        RequestStatus to = RequestStatus.valueOf(body.status().trim().toUpperCase());
        RequestStatusTransition.validate(from, to);

        Instant now = Instant.now();
        maybeRefundOnCancel(request, from, to, requestId);

        request.setStatus(to.toDb());
        request.setStatusChangedAt(now);
        request.setStatusChangedBy(actorId);
        if (body.assignedToUserId() != null) {
            request.setAssignedToUserId(body.assignedToUserId());
        }
        request.setUpdatedAt(now);
        requestRepository.save(request);

        eventRepository.save(CourseraRequestStatusEventEntity.create(
                UUID.randomUUID(),
                requestId,
                from.toDb(),
                to.toDb(),
                actorId,
                body.note(),
                now));

        return getDetail(requestId);
    }

    private void maybeRefundOnCancel(
            CourseraServiceRequestEntity request,
            RequestStatus from,
            RequestStatus to,
            UUID requestId) {
        if (to != RequestStatus.CANCELLED || !properties.refundOnCancel()) {
            return;
        }
        if (from != RequestStatus.PENDING && from != RequestStatus.IN_PROGRESS) {
            return;
        }
        if (request.getRefundLedgerId() != null || request.getPaymentLedgerId() == null) {
            return;
        }
        PointsLedgerEntity refund = walletService.credit(
                request.getUserId(),
                request.getTotalPoints(),
                "Refund cancelled Coursera request",
                PointsWalletService.SOURCE_COURSERA_REQUEST,
                requestId);
        request.setRefundLedgerId(refund.getId());
    }

    private RequestStatsResponse buildStats(UUID userId) {
        long total = userId == null ? requestRepository.count() : requestRepository.countByUserId(userId);
        long pending = 0;
        long inProgress = 0;
        long completed = 0;
        long cancelled = 0;
        var statusCounts = userId == null
                ? requestRepository.countByStatusAll()
                : requestRepository.countByStatusForUser(userId);
        for (Object[] row : statusCounts) {
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

    private static Instant resolveTo(String period) {
        if (period == null || period.isBlank() || "all".equalsIgnoreCase(period)) {
            return null;
        }
        return Instant.now();
    }

    private static Instant resolveFrom(String period) {
        if (period == null || period.isBlank() || "all".equalsIgnoreCase(period)) {
            return null;
        }
        Instant now = Instant.now();
        return switch (period.toLowerCase()) {
            case "7d" -> now.minus(7, ChronoUnit.DAYS);
            case "30d" -> now.minus(30, ChronoUnit.DAYS);
            case "90d" -> now.minus(90, ChronoUnit.DAYS);
            default -> null;
        };
    }
}
