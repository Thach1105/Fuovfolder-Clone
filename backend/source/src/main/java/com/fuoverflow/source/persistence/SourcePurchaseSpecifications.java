package com.fuoverflow.source.persistence;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SourcePurchaseSpecifications {
    private SourcePurchaseSpecifications() {
    }

    public static Specification<SourcePurchaseEntity> adminSearch(
            UUID userId,
            UUID catalogItemId,
            String status,
            String code,
            Instant from,
            Instant to) {
        return (root, query, cb) -> {
            query.orderBy(cb.desc(root.get("createdAt")));
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (catalogItemId != null) {
                predicates.add(cb.equal(root.get("catalogItemId"), catalogItemId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (code != null && !code.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("codeSnapshot")), "%" + code.toLowerCase() + "%"));
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
