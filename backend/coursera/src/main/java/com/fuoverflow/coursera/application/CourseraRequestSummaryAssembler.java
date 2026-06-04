package com.fuoverflow.coursera.application;

import com.fuoverflow.coursera.api.dto.RequestSummaryResponse;
import com.fuoverflow.coursera.persistence.CourseraCatalogItemRepository;
import com.fuoverflow.coursera.persistence.CourseraRequestItemEntity;
import com.fuoverflow.coursera.persistence.CourseraRequestItemRepository;
import com.fuoverflow.coursera.persistence.CourseraServiceRequestEntity;
import com.fuoverflow.user.persistence.UserRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Batches request line items, catalog codes, and usernames to avoid N+1 on list endpoints.
 */
public final class CourseraRequestSummaryAssembler {
    private CourseraRequestSummaryAssembler() {
    }

    public static BatchContext loadBatch(
            List<CourseraServiceRequestEntity> requests,
            CourseraRequestItemRepository itemRepository,
            CourseraCatalogItemRepository catalogRepository,
            UserRepository userRepository,
            boolean includeUsernames) {
        if (requests.isEmpty()) {
            return new BatchContext(Map.of(), Map.of(), Map.of());
        }
        List<UUID> requestIds = requests.stream().map(CourseraServiceRequestEntity::getId).toList();

        Map<UUID, List<CourseraRequestItemEntity>> itemsByRequest =
                itemRepository.findByRequestIdIn(requestIds).stream()
                        .collect(Collectors.groupingBy(CourseraRequestItemEntity::getRequestId));

        Set<UUID> catalogIds = itemsByRequest.values().stream()
                .flatMap(List::stream)
                .map(CourseraRequestItemEntity::getCatalogItemId)
                .collect(Collectors.toSet());
        Map<UUID, String> catalogCodes = new HashMap<>();
        if (!catalogIds.isEmpty()) {
            catalogRepository.findAllById(catalogIds).forEach(c -> catalogCodes.put(c.getId(), c.getCode()));
        }

        Map<UUID, String> usernames = new HashMap<>();
        if (includeUsernames) {
            Set<UUID> userIds = requests.stream()
                    .map(CourseraServiceRequestEntity::getUserId)
                    .collect(Collectors.toSet());
            userRepository.findAllById(userIds).forEach(u -> usernames.put(u.getId(), u.getUsername()));
        }

        return new BatchContext(itemsByRequest, catalogCodes, usernames);
    }

    public static RequestSummaryResponse toSummary(CourseraServiceRequestEntity request, BatchContext batch) {
        List<CourseraRequestItemEntity> items = batch.itemsByRequest().getOrDefault(request.getId(), List.of());
        String code = "";
        String title = "";
        if (!items.isEmpty()) {
            CourseraRequestItemEntity first = items.getFirst();
            title = first.getItemTitleSnapshot();
            code = batch.catalogCodes().getOrDefault(first.getCatalogItemId(), "");
        }
        String username = batch.usernames().isEmpty()
                ? null
                : batch.usernames().getOrDefault(request.getUserId(), "—");
        return new RequestSummaryResponse(
                request.getId(),
                request.getStatus(),
                request.getTotalPoints(),
                code,
                title,
                username,
                request.getCreatedAt(),
                request.getStatusChangedAt());
    }

    public record BatchContext(
            Map<UUID, List<CourseraRequestItemEntity>> itemsByRequest,
            Map<UUID, String> catalogCodes,
            Map<UUID, String> usernames) {
    }
}
