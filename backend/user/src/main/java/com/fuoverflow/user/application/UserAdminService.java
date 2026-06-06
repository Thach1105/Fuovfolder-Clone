package com.fuoverflow.user.application;

import com.fuoverflow.user.api.dto.AdminOverviewResponse;
import com.fuoverflow.user.api.dto.AdminUserPageResponse;
import com.fuoverflow.user.api.dto.AdminUserSummaryResponse;
import com.fuoverflow.user.domain.UserStatus;
import com.fuoverflow.user.persistence.UserMapper;
import com.fuoverflow.user.persistence.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
