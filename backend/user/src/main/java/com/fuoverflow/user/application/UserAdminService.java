package com.fuoverflow.user.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.user.api.dto.AdminOverviewResponse;
import com.fuoverflow.user.api.dto.AdminUserPageResponse;
import com.fuoverflow.user.api.dto.AdminUserSummaryResponse;
import com.fuoverflow.user.domain.UserRole;
import com.fuoverflow.user.domain.UserStatus;
import com.fuoverflow.user.persistence.UserEntity;
import com.fuoverflow.user.persistence.UserMapper;
import com.fuoverflow.user.persistence.UserRepository;
import com.fuoverflow.user.support.PermissionJson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserAdminService {
    private static final int MAX_PAGE_SIZE = 50;

    private final UserRepository repository;
    private final UserMapper mapper;

    public UserAdminService(UserRepository repository, UserMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse getOverview() {
        return new AdminOverviewResponse(
                repository.countByDeletedAtIsNull(),
                repository.countByDeletedAtIsNullAndStatus(UserStatus.ACTIVE),
                repository.countByDeletedAtIsNullAndStatus(UserStatus.PENDING_EMAIL_VERIFICATION),
                repository.countByDeletedAtIsNullAndStatus(UserStatus.DISABLED),
                repository.countByRole("[\"SUPER_ADMIN\"]"),
                repository.countByRole("[\"ADMIN\"]"),
                repository.countByRole("[\"SUB_ADMIN\"]")
        );
    }

    /**
     * Soft-delete a user: set deleted_at + status DELETED. Does NOT touch related rows
     * (posts, payments, ...) — the DB has no FKs and references stay intact. The partial
     * unique indexes on lower(username)/lower(email) WHERE deleted_at IS NULL free up the
     * handle for re-registration once deleted_at is set.
     */
    @Transactional
    public void softDeleteUser(UUID targetId, UUID actingAdminId) {
        if (targetId.equals(actingAdminId)) {
            throw new BadRequestException("USER_SELF_DELETE", "Không thể tự xóa tài khoản của mình");
        }
        UserEntity user = repository.findById(targetId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng"));
        if (user.getDeletedAt() != null) {
            throw new BadRequestException("USER_ALREADY_DELETED", "Tài khoản đã bị xóa");
        }
        if (hasPrivilegedRole(user)) {
            throw new BadRequestException("USER_IS_STAFF", "Không thể xóa tài khoản quản trị");
        }
        user.markDeleted(Instant.now());
        repository.save(user);
    }

    private boolean hasPrivilegedRole(UserEntity user) {
        List<String> roles = PermissionJson.parseSlugs(user.getRolesJson());
        return roles.contains(UserRole.SUB_ADMIN.name())
                || roles.contains(UserRole.ADMIN.name())
                || roles.contains(UserRole.SUPER_ADMIN.name());
    }

    @Transactional(readOnly = true)
    public AdminUserPageResponse listUsers(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<AdminUserSummaryResponse> mapped = repository
                .findByDeletedAtIsNullOrderByCreatedAtDesc(pageable)
                .map(mapper::toAdminSummary);
        return new AdminUserPageResponse(
                mapped.getContent(),
                mapped.getNumber(),
                mapped.getSize(),
                mapped.getTotalElements(),
                mapped.getTotalPages()
        );
    }
}
