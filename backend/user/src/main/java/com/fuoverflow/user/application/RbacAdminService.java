package com.fuoverflow.user.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.user.api.dto.CreateRoleRequest;
import com.fuoverflow.user.api.dto.PermissionCatalogResponse;
import com.fuoverflow.user.api.dto.PermissionItemResponse;
import com.fuoverflow.user.api.dto.RoleDetailResponse;
import com.fuoverflow.user.api.dto.RoleSummaryResponse;
import com.fuoverflow.user.api.dto.UpdateRolePermissionsRequest;
import com.fuoverflow.user.api.dto.UpdateUserOverridesRequest;
import com.fuoverflow.user.api.dto.UpdateUserRolesRequest;
import com.fuoverflow.user.api.dto.UserOverrideItemRequest;
import com.fuoverflow.user.api.dto.UserPermissionOverrideResponse;
import com.fuoverflow.user.domain.PermissionEffect;
import com.fuoverflow.user.domain.RoleType;
import com.fuoverflow.user.persistence.PermissionEntity;
import com.fuoverflow.user.persistence.PermissionRepository;
import com.fuoverflow.user.persistence.RoleAssignmentEntity;
import com.fuoverflow.user.persistence.RoleAssignmentRepository;
import com.fuoverflow.user.persistence.RoleEntity;
import com.fuoverflow.user.persistence.RoleRepository;
import com.fuoverflow.user.persistence.UserPermissionOverrideEntity;
import com.fuoverflow.user.persistence.UserPermissionOverrideRepository;
import com.fuoverflow.user.persistence.UserRepository;
import com.fuoverflow.user.support.PermissionJson;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RbacAdminService {
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository assignmentRepository;
    private final UserPermissionOverrideRepository overrideRepository;
    private final UserRepository userRepository;
    private final RoleAssignmentService roleAssignmentService;
    private final PermissionResolverService permissionResolver;

    public RbacAdminService(
            PermissionRepository permissionRepository,
            RoleRepository roleRepository,
            RoleAssignmentRepository assignmentRepository,
            UserPermissionOverrideRepository overrideRepository,
            UserRepository userRepository,
            RoleAssignmentService roleAssignmentService,
            PermissionResolverService permissionResolver) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.assignmentRepository = assignmentRepository;
        this.overrideRepository = overrideRepository;
        this.userRepository = userRepository;
        this.roleAssignmentService = roleAssignmentService;
        this.permissionResolver = permissionResolver;
    }

    @Transactional(readOnly = true)
    public PermissionCatalogResponse listPermissions() {
        List<PermissionEntity> all = permissionRepository.findAllByOrderByModuleAscResourceAscActionAsc();
        Map<String, List<PermissionItemResponse>> grouped = new LinkedHashMap<>();
        for (PermissionEntity permission : all) {
            grouped.computeIfAbsent(permission.getModule(), key -> new ArrayList<>())
                    .add(new PermissionItemResponse(
                            permission.getSlug(),
                            permission.getModule(),
                            permission.getResource(),
                            permission.getAction(),
                            permission.getDescription()));
        }
        return new PermissionCatalogResponse(grouped);
    }

    @Transactional(readOnly = true)
    public List<RoleSummaryResponse> listRoles() {
        return roleRepository.findAllByOrderByRoleTypeAscSlugAsc().stream()
                .filter(role -> !PermissionResolverService.SUPER_ADMIN_SLUG.equals(role.getSlug()))
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleDetailResponse getRole(UUID roleId) {
        RoleEntity role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("ROLE_NOT_FOUND", "Role not found"));
        if (PermissionResolverService.SUPER_ADMIN_SLUG.equals(role.getSlug())) {
            throw new NotFoundException("ROLE_NOT_FOUND", "Role not found");
        }
        return toDetail(role);
    }

    @Transactional
    public RoleDetailResponse createRole(CreateRoleRequest request) {
        String slug = request.slug().trim().toUpperCase().replace(' ', '_');
        if (roleRepository.findBySlug(slug).isPresent()) {
            throw new ConflictException("ROLE_EXISTS", "Role slug already exists");
        }
        validatePermissionSlugs(request.permissions());
        UUID parentId = null;
        if (request.parentRoleSlug() != null && !request.parentRoleSlug().isBlank()) {
            parentId = roleRepository.findBySlug(request.parentRoleSlug().trim())
                    .map(RoleEntity::getId)
                    .orElseThrow(() -> new BadRequestException("PARENT_ROLE_INVALID", "Parent role not found"));
        }
        Instant now = Instant.now();
        RoleEntity role = RoleEntity.createCustom(
                UUID.randomUUID(),
                slug,
                request.name().trim(),
                RoleType.STAFF,
                parentId,
                request.permissions(),
                now);
        roleRepository.save(role);
        return toDetail(role);
    }

    @Transactional
    public RoleDetailResponse updateRolePermissions(UUID roleId, UpdateRolePermissionsRequest request) {
        RoleEntity role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("ROLE_NOT_FOUND", "Role not found"));
        if (!role.isEditable()) {
            throw new BadRequestException("ROLE_NOT_EDITABLE", "This role cannot be edited");
        }
        validatePermissionSlugs(request.permissions());
        role.setPermissionsJson(PermissionJson.toJson(request.permissions()));
        role.setUpdatedAt(Instant.now());
        roleRepository.save(role);
        permissionResolver.invalidate(null); // clear all - simpler: bump all users with role - for MVP invalidate cache globally
        return toDetail(role);
    }

    @Transactional(readOnly = true)
    public List<String> getUserRoleSlugs(UUID userId) {
        requireUser(userId);
        return permissionResolver.resolve(userId).roles();
    }

    @Transactional
    public List<String> updateUserRoles(UUID userId, UpdateUserRolesRequest request, UUID actorId) {
        requireUser(userId);
        List<RoleEntity> allRoles = roleRepository.findAll();
        Map<String, UUID> slugToId = allRoles.stream()
                .collect(java.util.stream.Collectors.toMap(RoleEntity::getSlug, RoleEntity::getId));
        Instant now = Instant.now();
        List<RoleAssignmentEntity> active = assignmentRepository.findActiveByUserId(userId, now);
        var requested = new java.util.HashSet<>(request.roleSlugs());
        for (RoleAssignmentEntity assignment : active) {
            RoleEntity role = allRoles.stream().filter(r -> r.getId().equals(assignment.getRoleId())).findFirst().orElse(null);
            if (role == null) {
                continue;
            }
            if (!requested.contains(role.getSlug()) && role.getRoleType() != RoleType.SYSTEM) {
                assignment.revoke(now);
                assignmentRepository.save(assignment);
            }
        }
        for (String slug : requested) {
            if (!slugToId.containsKey(slug)) {
                throw new BadRequestException("ROLE_INVALID", "Unknown role: " + slug);
            }
            roleAssignmentService.assignGlobalRole(userId, slug, actorId);
        }
        roleAssignmentService.assignGlobalRole(userId, "USER", actorId);
        return permissionResolver.resolve(userId).roles();
    }

    @Transactional(readOnly = true)
    public List<UserPermissionOverrideResponse> getUserOverrides(UUID userId) {
        requireUser(userId);
        return overrideRepository.findActiveByUserId(userId, Instant.now()).stream()
                .map(o -> new UserPermissionOverrideResponse(o.getPermissionSlug(), o.getEffect().name(), o.getReason()))
                .toList();
    }

    @Transactional
    public List<UserPermissionOverrideResponse> updateUserOverrides(UUID userId, UpdateUserOverridesRequest request,
                                                                    UUID actorId) {
        requireUser(userId);
        Instant now = Instant.now();
        overrideRepository.findActiveByUserId(userId, now).forEach(o -> {
            o.revoke(now);
            overrideRepository.save(o);
        });
        for (UserOverrideItemRequest item : request.overrides()) {
            validatePermissionSlugs(List.of(item.permissionSlug()));
            overrideRepository.save(UserPermissionOverrideEntity.create(
                    UUID.randomUUID(),
                    userId,
                    item.permissionSlug(),
                    PermissionEffect.valueOf(item.effect().toUpperCase()),
                    item.reason(),
                    actorId,
                    now));
        }
        permissionResolver.bumpVersion(userId);
        return getUserOverrides(userId);
    }

    @Transactional(readOnly = true)
    public com.fuoverflow.user.api.dto.EffectivePermissions getEffectivePermissions(UUID userId) {
        requireUser(userId);
        return permissionResolver.resolve(userId);
    }

    private void requireUser(UUID userId) {
        if (!userRepository.existsByIdAndDeletedAtIsNull(userId)) {
            throw new NotFoundException("USER_NOT_FOUND", "User not found");
        }
    }

    private void validatePermissionSlugs(List<String> slugs) {
        for (String slug : slugs) {
            if (!permissionRepository.existsById(slug)) {
                throw new BadRequestException("PERMISSION_INVALID", "Unknown permission: " + slug);
            }
        }
    }

    private RoleSummaryResponse toSummary(RoleEntity role) {
        return new RoleSummaryResponse(
                role.getId(),
                role.getSlug(),
                role.getName(),
                role.getRoleType().name(),
                role.getParentRoleId(),
                role.isSystem(),
                role.isEditable(),
                PermissionJson.parseSlugs(role.getPermissionsJson()).size());
    }

    private RoleDetailResponse toDetail(RoleEntity role) {
        return new RoleDetailResponse(
                role.getId(),
                role.getSlug(),
                role.getName(),
                role.getRoleType().name(),
                role.getParentRoleId(),
                role.isSystem(),
                role.isEditable(),
                PermissionJson.parseSlugs(role.getPermissionsJson()));
    }
}
