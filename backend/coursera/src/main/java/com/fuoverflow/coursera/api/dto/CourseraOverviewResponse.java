package com.fuoverflow.coursera.api.dto;

public record CourseraOverviewResponse(
        long totalRequests,
        long pending,
        long inProgress,
        long completed,
        long cancelled,
        long activeCatalogItems
) {
}
