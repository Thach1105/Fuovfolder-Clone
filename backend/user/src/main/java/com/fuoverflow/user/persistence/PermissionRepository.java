package com.fuoverflow.user.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermissionRepository extends JpaRepository<PermissionEntity, String> {
    List<PermissionEntity> findAllByOrderByModuleAscResourceAscActionAsc();
}
