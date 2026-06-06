package com.fuoverflow.user.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.user.persistence.RoleAssignmentEntity;
import com.fuoverflow.user.persistence.RoleAssignmentRepository;
import com.fuoverflow.user.persistence.RoleEntity;
import com.fuoverflow.user.persistence.RoleRepository;
import com.fuoverflow.user.persistence.UserPermissionVersionEntity;
import com.fuoverflow.user.persistence.UserPermissionVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RoleAssignmentService {
    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository assignmentRepository;
    private final UserPermissionVersionRepository versionRepository;
    private final PermissionResolverService permissionResolver;

    public RoleAssignmentService(
            RoleRepository roleRepository,
            RoleAssignmentRepository assignmentRepository,
            UserPermissionVersionRepository versionRepository,
            PermissionResolverService permissionResolver) {
        this.roleRepository = roleRepository;
        this.assignmentRepository = assignmentRepository;
        this.versionRepository = versionRepository;
        this.permissionResolver = permissionResolver;
    }

    @Transactional
    public void assignGlobalRole(UUID userId, String roleSlug, UUID assignedBy) {
        RoleEntity role = roleRepository.findBySlug(roleSlug)
                .orElseThrow(() -> new NotFoundException("ROLE_NOT_FOUND", "Role not found"));
        if (!assignmentRepository.findActiveGlobalByUserAndRole(userId, role.getId()).isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        assignmentRepository.save(RoleAssignmentEntity.create(UUID.randomUUID(), userId, role.getId(), assignedBy, now));
        bumpVersion(userId, now);
    }

    @Transactional
    public void revokeGlobalRole(UUID userId, String roleSlug) {
        RoleEntity role = roleRepository.findBySlug(roleSlug)
                .orElseThrow(() -> new NotFoundException("ROLE_NOT_FOUND", "Role not found"));
        Instant now = Instant.now();
        assignmentRepository.findActiveGlobalByUserAndRole(userId, role.getId())
                .forEach(assignment -> {
                    assignment.revoke(now);
                    assignmentRepository.save(assignment);
                });
        bumpVersion(userId, now);
    }

    @Transactional
    public void ensureDefaultUserRole(UUID userId) {
        assignGlobalRole(userId, "USER", null);
    }

    private void bumpVersion(UUID userId, Instant now) {
        UserPermissionVersionEntity version = versionRepository.findById(userId)
                .orElseGet(() -> UserPermissionVersionEntity.initial(userId, now));
        version.bump(now);
        versionRepository.save(version);
        permissionResolver.invalidate(userId);
    }
}
