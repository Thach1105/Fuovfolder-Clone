package com.fuoverflow.user.application;

import com.fuoverflow.user.api.dto.EffectivePermissions;
import com.fuoverflow.user.persistence.PermissionRepository;
import com.fuoverflow.user.persistence.RoleAssignmentEntity;
import com.fuoverflow.user.persistence.RoleAssignmentRepository;
import com.fuoverflow.user.persistence.RoleEntity;
import com.fuoverflow.user.persistence.RoleRepository;
import com.fuoverflow.user.persistence.UserPermissionOverrideEntity;
import com.fuoverflow.user.persistence.UserPermissionOverrideRepository;
import com.fuoverflow.user.persistence.UserPermissionVersionEntity;
import com.fuoverflow.user.persistence.UserPermissionVersionRepository;
import com.fuoverflow.user.support.PermissionJson;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class PermissionResolverService {
    public static final String SUPER_ADMIN_SLUG = "SUPER_ADMIN";

    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository assignmentRepository;
    private final UserPermissionOverrideRepository overrideRepository;
    private final UserPermissionVersionRepository versionRepository;
    private final PermissionRepository permissionRepository;
    private final Map<UUID, CachedPermissions> cache = new ConcurrentHashMap<>();

    public PermissionResolverService(
            RoleRepository roleRepository,
            RoleAssignmentRepository assignmentRepository,
            UserPermissionOverrideRepository overrideRepository,
            UserPermissionVersionRepository versionRepository,
            PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.assignmentRepository = assignmentRepository;
        this.overrideRepository = overrideRepository;
        this.versionRepository = versionRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public EffectivePermissions resolve(UUID userId) {
        long version = versionRepository.findById(userId).map(UserPermissionVersionEntity::getVersion).orElse(1L);
        CachedPermissions cached = cache.get(userId);
        if (cached != null && cached.version() == version) {
            return cached.permissions();
        }
        EffectivePermissions resolved = compute(userId, version);
        cache.put(userId, new CachedPermissions(version, resolved));
        return resolved;
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(UUID userId, String permissionSlug) {
        return resolve(userId).permissions().contains(permissionSlug);
    }

    @Transactional(readOnly = true)
    public List<String> resolveRoleSlugs(UUID userId) {
        return resolve(userId).roles();
    }

    public void invalidate(UUID userId) {
        if (userId == null) {
            cache.clear();
            return;
        }
        cache.remove(userId);
    }

    @Transactional
    public long bumpVersion(UUID userId) {
        Instant now = Instant.now();
        UserPermissionVersionEntity version = versionRepository.findById(userId)
                .orElseGet(() -> UserPermissionVersionEntity.initial(userId, now));
        version.bump(now);
        versionRepository.save(version);
        invalidate(userId);
        return version.getVersion();
    }

    private EffectivePermissions compute(UUID userId, long version) {
        Instant now = Instant.now();
        Map<UUID, RoleEntity> rolesById = roleRepository.findAll().stream()
                .collect(Collectors.toMap(RoleEntity::getId, role -> role));
        List<RoleAssignmentEntity> assignments = assignmentRepository.findActiveByUserId(userId, now);
        List<String> roleSlugs = new ArrayList<>();
        Set<String> permissions = new HashSet<>();

        for (RoleAssignmentEntity assignment : assignments) {
            RoleEntity role = rolesById.get(assignment.getRoleId());
            if (role == null) {
                continue;
            }
            roleSlugs.add(role.getSlug());
            if (SUPER_ADMIN_SLUG.equals(role.getSlug())) {
                List<String> all = permissionRepository.findAll().stream()
                        .map(p -> p.getSlug())
                        .toList();
                return new EffectivePermissions(userId, version, roleSlugs, all, true);
            }
            permissions.addAll(collectRolePermissions(role, rolesById));
        }

        for (UserPermissionOverrideEntity override : overrideRepository.findActiveByUserId(userId, now)) {
            if (override.getEffect().name().equals("GRANT")) {
                permissions.add(override.getPermissionSlug());
            } else {
                permissions.remove(override.getPermissionSlug());
            }
        }

        return new EffectivePermissions(userId, version, roleSlugs,
                permissions.stream().sorted().toList(), false);
    }

    private Set<String> collectRolePermissions(RoleEntity role, Map<UUID, RoleEntity> rolesById) {
        Set<String> collected = new HashSet<>();
        RoleEntity current = role;
        while (current != null) {
            collected.addAll(PermissionJson.parseSlugs(current.getPermissionsJson()));
            current = current.getParentRoleId() == null ? null : rolesById.get(current.getParentRoleId());
        }
        return collected;
    }

    private record CachedPermissions(long version, EffectivePermissions permissions) {
    }
}
