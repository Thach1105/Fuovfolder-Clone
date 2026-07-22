package com.fuoverflow.broadcast.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.broadcast.api.dto.AnnouncementActiveResponse;
import com.fuoverflow.broadcast.api.dto.AnnouncementRequest;
import com.fuoverflow.broadcast.api.dto.AnnouncementResponse;
import com.fuoverflow.broadcast.persistence.AnnouncementEntity;
import com.fuoverflow.broadcast.persistence.AnnouncementRepository;
import com.fuoverflow.common.broadcast.BroadcastMessage;
import com.fuoverflow.common.broadcast.BroadcastPublisher;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnnouncementService {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementService.class);
    private static final String EVENT_TYPE = "announcement.sync";

    private final AnnouncementRepository repo;
    private final BroadcastPublisher publisher;
    private final ObjectMapper objectMapper;

    public AnnouncementService(AnnouncementRepository repo,
                               BroadcastPublisher publisher,
                               ObjectMapper objectMapper) {
        this.repo = repo;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AnnouncementResponse create(AnnouncementRequest request, String status, UUID adminUserId) {
        validateTimeRange(request);
        AnnouncementEntity entity = AnnouncementEntity.create(
                request.title(), request.contentHtml(), request.backgroundColor(),
                request.linkUrl(), request.linkLabel(),
                request.priority(), request.scrollSpeed(), request.stepSeconds(),
                status, request.startAt(), request.endAt(), adminUserId);
        AnnouncementResponse response = toResponse(repo.save(entity));
        if ("ACTIVE".equals(status)) {
            publishSync();
        }
        return response;
    }

    @Transactional
    public AnnouncementResponse update(UUID id, AnnouncementRequest request) {
        validateTimeRange(request);
        AnnouncementEntity entity = findById(id);
        String currentStatus = entity.getStatus();

        if ("DRAFT".equals(currentStatus) || "SCHEDULED".equals(currentStatus)) {
            entity.updateFull(
                    request.title(), request.contentHtml(), request.backgroundColor(),
                    request.linkUrl(), request.linkLabel(),
                    request.priority(), request.scrollSpeed(), request.stepSeconds(),
                    request.startAt(), request.endAt());
        } else if ("ACTIVE".equals(currentStatus)) {
            entity.updateContent(
                    request.contentHtml(), request.backgroundColor(),
                    request.linkUrl(), request.linkLabel(),
                    request.priority(), request.scrollSpeed(), request.stepSeconds());
        } else {
            throw new BadRequestException("ANNOUNCEMENT_NOT_EDITABLE",
                    "Cannot edit announcement with status: " + currentStatus);
        }

        AnnouncementResponse response = toResponse(repo.save(entity));
        if ("ACTIVE".equals(currentStatus)) {
            publishSync();
        }
        return response;
    }

    @Transactional
    public void delete(UUID id) {
        AnnouncementEntity entity = findById(id);
        if ("ACTIVE".equals(entity.getStatus()) || "EXPIRED".equals(entity.getStatus())) {
            throw new BadRequestException("ANNOUNCEMENT_CANNOT_DELETE",
                    "Cannot delete announcement with status: " + entity.getStatus());
        }
        repo.delete(entity);
    }

    @Transactional
    public AnnouncementResponse activate(UUID id) {
        AnnouncementEntity entity = findById(id);
        entity.updateStatus("ACTIVE");
        AnnouncementResponse response = toResponse(repo.save(entity));
        publishSync();
        return response;
    }

    @Transactional
    public AnnouncementResponse deactivate(UUID id) {
        AnnouncementEntity entity = findById(id);
        if (!"ACTIVE".equals(entity.getStatus())) {
            throw new BadRequestException("ANNOUNCEMENT_NOT_ACTIVE",
                    "Can only deactivate an active announcement");
        }
        entity.updateStatus("EXPIRED");
        AnnouncementResponse response = toResponse(repo.save(entity));
        publishSync();
        return response;
    }

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> listByStatus(String status) {
        List<AnnouncementEntity> entities = (status == null || status.isBlank())
                ? repo.findAll()
                : repo.findByStatus(status);
        return entities.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AnnouncementResponse getById(UUID id) {
        return toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public List<AnnouncementActiveResponse> getActiveForUsers() {
        return repo.findByStatusOrderByPriorityDesc("ACTIVE").stream()
                .map(this::toActiveResponse)
                .toList();
    }

    public void publishSync() {
        List<AnnouncementActiveResponse> active = getActiveForUsers();
        Map<String, Object> data = Map.of("announcements", active);
        String message;
        try {
            message = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.error("Failed to serialize announcement sync payload", e);
            return;
        }
        publisher.publish(new BroadcastMessage(EVENT_TYPE, message, data));
    }

    public void sendInitialState(SseEmitter emitter) {
        List<AnnouncementActiveResponse> active = getActiveForUsers();
        if (active.isEmpty()) return;
        Map<String, Object> data = Map.of("announcements", active);
        try {
            String json = objectMapper.writeValueAsString(
                    new BroadcastMessage(EVENT_TYPE, objectMapper.writeValueAsString(data), data));
            emitter.send(SseEmitter.event().name(EVENT_TYPE).data(json));
        } catch (IOException e) {
            log.debug("Failed to send initial announcement state", e);
        }
    }

    private void validateTimeRange(AnnouncementRequest request) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw new BadRequestException("INVALID_TIME_RANGE",
                    "End time must be after start time");
        }
    }

    private AnnouncementEntity findById(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "ANNOUNCEMENT_NOT_FOUND", "Announcement not found: " + id));
    }

    private AnnouncementResponse toResponse(AnnouncementEntity e) {
        return new AnnouncementResponse(
                e.getId(), e.getTitle(), e.getContentHtml(), e.getBackgroundColor(),
                e.getLinkUrl(), e.getLinkLabel(), e.getPriority(), e.getScrollSpeed(),
                e.getStepSeconds(), e.getStatus(), e.getStartAt(), e.getEndAt(),
                e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private AnnouncementActiveResponse toActiveResponse(AnnouncementEntity e) {
        return new AnnouncementActiveResponse(
                e.getId(), e.getContentHtml(), e.getBackgroundColor(),
                e.getLinkUrl(), e.getLinkLabel(), e.getPriority(),
                e.getScrollSpeed(), e.getStepSeconds());
    }
}
