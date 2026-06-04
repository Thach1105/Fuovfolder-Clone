package com.fuoverflow.coursera.persistence;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;

public final class CourseraServiceRequestSpecifications {
    private CourseraServiceRequestSpecifications() {
    }

    public static Specification<CourseraServiceRequestEntity> adminSearch(
            UUID userId,
            UUID catalogItemId,
            String status,
            Instant from,
            Instant to) {
        return (root, query, cb) -> {
            query.orderBy(cb.desc(root.get("createdAt")));
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (catalogItemId != null) {
                Subquery<UUID> sub = query.subquery(UUID.class);
                var itemRoot = sub.from(CourseraRequestItemEntity.class);
                sub.select(itemRoot.get("requestId"))
                        .where(cb.equal(itemRoot.get("catalogItemId"), catalogItemId));
                predicates.add(root.get("id").in(sub));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), to));
            }
            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
