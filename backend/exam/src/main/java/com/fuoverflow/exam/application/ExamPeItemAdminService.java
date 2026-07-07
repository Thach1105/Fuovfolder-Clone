package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.exam.api.dto.AddPeResourceRequest;
import com.fuoverflow.exam.api.dto.AdminPeItemResponse;
import com.fuoverflow.exam.api.dto.CreatePeItemRequest;
import com.fuoverflow.exam.api.dto.UpdatePeItemRequest;
import com.fuoverflow.exam.persistence.ExamPeItemEntity;
import com.fuoverflow.exam.persistence.ExamPeItemRepository;
import com.fuoverflow.exam.persistence.ExamPeResourceEntity;
import com.fuoverflow.exam.persistence.ExamPeResourceRepository;
import com.fuoverflow.exam.persistence.ExamSubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ExamPeItemAdminService {
    private final ExamPeItemRepository peItemRepository;
    private final ExamPeResourceRepository peResourceRepository;
    private final ExamSubjectRepository subjectRepository;
    private final ExamMediaService mediaService;
    private final ExamMediaUrlResolver urlResolver;
    private final ObjectMapper objectMapper;

    public ExamPeItemAdminService(
            ExamPeItemRepository peItemRepository,
            ExamPeResourceRepository peResourceRepository,
            ExamSubjectRepository subjectRepository,
            ExamMediaService mediaService,
            ExamMediaUrlResolver urlResolver,
            ObjectMapper objectMapper) {
        this.peItemRepository = peItemRepository;
        this.peResourceRepository = peResourceRepository;
        this.subjectRepository = subjectRepository;
        this.mediaService = mediaService;
        this.urlResolver = urlResolver;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AdminPeItemResponse> list(UUID subjectId) {
        requireSubject(subjectId);
        return peItemRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subjectId).stream()
                .map(this::toAdmin)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminPeItemResponse get(UUID subjectId, UUID itemId) {
        return toAdmin(requireItem(subjectId, itemId));
    }

    @Transactional
    public AdminPeItemResponse create(UUID subjectId, CreatePeItemRequest request) {
        requireSubject(subjectId);
        List<String> imageKeys = normalizeImages(request.examImageUrls());
        Instant now = Instant.now();
        int sortOrder = request.sortOrder() != null
                ? request.sortOrder()
                : (int) peItemRepository.countBySubjectIdAndDeletedAtIsNull(subjectId);
        UUID itemId = UUID.randomUUID();
        ExamPeItemEntity entity = ExamPeItemEntity.create(
                itemId,
                subjectId,
                request.title().trim(),
                blankToNull(request.description()),
                ExamJsonUtil.serialize(objectMapper, imageKeys),
                sortOrder,
                now);
        peItemRepository.save(entity);
        mediaService.markLinkedAll(imageKeys);
        return get(subjectId, itemId);
    }

    @Transactional
    public AdminPeItemResponse update(UUID subjectId, UUID itemId, UpdatePeItemRequest request) {
        ExamPeItemEntity entity = requireItem(subjectId, itemId);
        if (request.title() != null) {
            entity.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            entity.setDescription(blankToNull(request.description()));
        }
        if (request.examImageUrls() != null) {
            List<String> oldKeys = ExamJsonUtil.deserialize(objectMapper, entity.getExamImageUrls());
            List<String> newKeys = normalizeImages(request.examImageUrls());
            for (String oldKey : oldKeys) {
                if (!newKeys.contains(oldKey)) {
                    mediaService.unlinkStoredReference(oldKey);
                }
            }
            entity.setExamImageUrls(ExamJsonUtil.serialize(objectMapper, newKeys));
            mediaService.markLinkedAll(newKeys);
        }
        if (request.sortOrder() != null) {
            entity.setSortOrder(request.sortOrder());
        }
        entity.setUpdatedAt(Instant.now());
        peItemRepository.save(entity);
        return get(subjectId, itemId);
    }

    @Transactional
    public void delete(UUID subjectId, UUID itemId) {
        ExamPeItemEntity entity = requireItem(subjectId, itemId);
        mediaService.deleteStoredReferences(ExamJsonUtil.deserialize(objectMapper, entity.getExamImageUrls()));
        List<ExamPeResourceEntity> resources = peResourceRepository.findByPeItemIdAndDeletedAtIsNullOrderBySortOrderAsc(itemId);
        Instant now = Instant.now();
        for (ExamPeResourceEntity r : resources) {
            mediaService.deleteStoredReference(r.getObjectKey());
            r.setDeletedAt(now);
            peResourceRepository.save(r);
        }
        entity.setDeletedAt(now);
        entity.setUpdatedAt(now);
        peItemRepository.save(entity);
    }

    @Transactional
    public AdminPeItemResponse addResource(UUID subjectId, UUID itemId, AddPeResourceRequest request) {
        requireItem(subjectId, itemId);
        String objectKey = urlResolver.normalizeForStorage(request.objectKey());
        Instant now = Instant.now();
        int sortOrder = request.sortOrder() != null
                ? request.sortOrder()
                : peResourceRepository.findByPeItemIdAndDeletedAtIsNullOrderBySortOrderAsc(itemId).size();
        ExamPeResourceEntity resource = ExamPeResourceEntity.create(
                UUID.randomUUID(),
                itemId,
                blankToNull(request.folderLabel()),
                objectKey,
                request.originalFilename().trim(),
                blankToNull(request.mimeType()),
                request.sizeBytes() != null ? request.sizeBytes() : 0L,
                sortOrder,
                now);
        peResourceRepository.save(resource);
        mediaService.markLinked(objectKey);
        return get(subjectId, itemId);
    }

    @Transactional
    public void deleteResource(UUID subjectId, UUID itemId, UUID resourceId) {
        requireItem(subjectId, itemId);
        ExamPeResourceEntity resource = peResourceRepository.findByIdAndDeletedAtIsNull(resourceId)
                .orElseThrow(() -> new NotFoundException("EXAM_PE_RESOURCE_NOT_FOUND", "Resource not found"));
        if (!resource.getPeItemId().equals(itemId)) {
            throw new NotFoundException("EXAM_PE_RESOURCE_NOT_FOUND", "Resource not found");
        }
        mediaService.deleteStoredReference(resource.getObjectKey());
        resource.setDeletedAt(Instant.now());
        peResourceRepository.save(resource);
    }

    // --- internals ------------------------------------------------------------

    private List<String> normalizeImages(List<String> imageUrls) {
        List<String> keys = new ArrayList<>();
        if (imageUrls != null) {
            for (String url : imageUrls) {
                String key = urlResolver.normalizeForStorage(url);
                if (key != null) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }

    private AdminPeItemResponse toAdmin(ExamPeItemEntity item) {
        List<String> imageKeys = ExamJsonUtil.deserialize(objectMapper, item.getExamImageUrls());
        List<AdminPeItemResponse.AdminPeResourceResponse> resources =
                peResourceRepository.findByPeItemIdAndDeletedAtIsNullOrderBySortOrderAsc(item.getId()).stream()
                        .map(r -> new AdminPeItemResponse.AdminPeResourceResponse(
                                r.getId(), r.getFolderLabel(), r.getObjectKey(), r.getOriginalFilename(),
                                r.getMimeType(), r.getSizeBytes(), r.getSortOrder()))
                        .toList();
        return new AdminPeItemResponse(
                item.getId(),
                item.getSubjectId(),
                item.getTitle(),
                item.getDescription(),
                urlResolver.plainAll(imageKeys),
                item.getSortOrder(),
                resources,
                item.getCreatedAt(),
                item.getUpdatedAt());
    }

    private void requireSubject(UUID subjectId) {
        if (subjectRepository.findByIdAndDeletedAtIsNull(subjectId).isEmpty()) {
            throw new NotFoundException("EXAM_SUBJECT_NOT_FOUND", "Exam subject not found");
        }
    }

    private ExamPeItemEntity requireItem(UUID subjectId, UUID itemId) {
        return peItemRepository.findByIdAndSubjectIdAndDeletedAtIsNull(itemId, subjectId)
                .orElseThrow(() -> new NotFoundException("EXAM_PE_ITEM_NOT_FOUND", "PE item not found"));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
