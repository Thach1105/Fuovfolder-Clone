package com.fuoverflow.user.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {
    Optional<RoleEntity> findBySlug(String slug);

    List<RoleEntity> findAllByOrderByRoleTypeAscSlugAsc();
}
