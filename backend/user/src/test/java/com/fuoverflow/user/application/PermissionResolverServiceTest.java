package com.fuoverflow.user.application;

import com.fuoverflow.user.persistence.PermissionEntity;
import com.fuoverflow.user.persistence.PermissionRepository;
import com.fuoverflow.user.persistence.RoleAssignmentEntity;
import com.fuoverflow.user.persistence.RoleAssignmentRepository;
import com.fuoverflow.user.persistence.RoleEntity;
import com.fuoverflow.user.persistence.RoleRepository;
import com.fuoverflow.user.persistence.UserPermissionOverrideEntity;
import com.fuoverflow.user.persistence.UserPermissionOverrideRepository;
import com.fuoverflow.user.persistence.UserPermissionVersionEntity;
import com.fuoverflow.user.persistence.UserPermissionVersionRepository;
import com.fuoverflow.user.domain.PermissionEffect;
import com.fuoverflow.user.domain.RoleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionResolverServiceTest {
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RoleAssignmentRepository assignmentRepository;
    @Mock
    private UserPermissionOverrideRepository overrideRepository;
    @Mock
    private UserPermissionVersionRepository versionRepository;
    @Mock
    private PermissionRepository permissionRepository;

    private PermissionResolverService resolver;

    private final UUID userId = UUID.randomUUID();
    private final UUID userRoleId = UUID.randomUUID();
    private final UUID memberRoleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        resolver = new PermissionResolverService(
                roleRepository, assignmentRepository, overrideRepository, versionRepository, permissionRepository);
        when(versionRepository.findById(userId))
                .thenReturn(Optional.of(UserPermissionVersionEntity.initial(userId, Instant.now())));
    }

    @Test
    void userWithoutMembershipCannotCreateThread() {
        stubRoles(userOnly());
        when(assignmentRepository.findActiveByUserId(eq(userId), any())).thenReturn(List.of(
                assignment(userRoleId)));
        when(overrideRepository.findActiveByUserId(eq(userId), any())).thenReturn(List.of());

        assertFalse(resolver.hasPermission(userId, "forum.thread:create"));
        assertTrue(resolver.hasPermission(userId, "forum.thread:read"));
    }

    @Test
    void memberInheritsCreateThreadPermission() {
        stubRoles(userAndMember());
        when(assignmentRepository.findActiveByUserId(eq(userId), any())).thenReturn(List.of(
                assignment(userRoleId), assignment(memberRoleId)));
        when(overrideRepository.findActiveByUserId(eq(userId), any())).thenReturn(List.of());

        assertTrue(resolver.hasPermission(userId, "forum.thread:create"));
    }

    @Test
    void superAdminBypassesAllChecks() {
        UUID superRoleId = UUID.randomUUID();
        RoleEntity superRole = role("SUPER_ADMIN", superRoleId, RoleType.SYSTEM, null, List.of());
        when(roleRepository.findAll()).thenReturn(List.of(superRole));
        when(assignmentRepository.findActiveByUserId(eq(userId), any()))
                .thenReturn(List.of(assignment(superRoleId)));
        PermissionEntity p1 = mock(PermissionEntity.class);
        PermissionEntity p2 = mock(PermissionEntity.class);
        when(p1.getSlug()).thenReturn("forum.thread:create");
        when(p2.getSlug()).thenReturn("rbac.role:update");
        when(permissionRepository.findAll()).thenReturn(List.of(p1, p2));

        assertTrue(resolver.hasPermission(userId, "rbac.role:update"));
    }

    @Test
    void denyOverrideRemovesGrantedPermission() {
        stubRoles(userAndMember());
        when(assignmentRepository.findActiveByUserId(eq(userId), any())).thenReturn(List.of(
                assignment(userRoleId), assignment(memberRoleId)));
        UserPermissionOverrideEntity deny = UserPermissionOverrideEntity.create(
                UUID.randomUUID(), userId, "forum.thread:create", PermissionEffect.DENY, null, null, Instant.now());
        when(overrideRepository.findActiveByUserId(eq(userId), any())).thenReturn(List.of(deny));

        assertFalse(resolver.hasPermission(userId, "forum.thread:create"));
    }

    private RoleAssignmentEntity assignment(UUID roleId) {
        return RoleAssignmentEntity.create(UUID.randomUUID(), userId, roleId, null, Instant.now());
    }

    private void stubRoles(List<RoleEntity> roles) {
        when(roleRepository.findAll()).thenReturn(roles);
    }

    private List<RoleEntity> userOnly() {
        return List.of(role("USER", userRoleId, RoleType.SYSTEM, null, List.of("forum.thread:read")));
    }

    private List<RoleEntity> userAndMember() {
        RoleEntity user = role("USER", userRoleId, RoleType.SYSTEM, null,
                List.of("forum.thread:read"));
        RoleEntity member = role("FUO_MEMBER", memberRoleId, RoleType.MEMBERSHIP, userRoleId,
                List.of("forum.thread:create"));
        return List.of(user, member);
    }

    private RoleEntity role(String slug, UUID id, RoleType type, UUID parentId, List<String> permissions) {
        return RoleEntity.createCustom(id, slug, slug, type, parentId, permissions, Instant.now());
    }
}
