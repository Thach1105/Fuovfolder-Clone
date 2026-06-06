package com.fuoverflow.user.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserPermissionVersionRepository extends JpaRepository<UserPermissionVersionEntity, UUID> {
}
