package com.fuoverflow.membership.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipPlanRepository extends JpaRepository<MembershipPlanEntity, UUID> {
    List<MembershipPlanEntity> findByStatusOrderByPriceCentsAsc(String status);

    List<MembershipPlanEntity> findAllByOrderByPriceCentsAsc();

    Optional<MembershipPlanEntity> findBySlug(String slug);

    Optional<MembershipPlanEntity> findBySlugAndStatus(String slug, String status);
}
