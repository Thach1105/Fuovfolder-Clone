package com.fuoverflow.user.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.user.api.dto.UpdateUserProfileRequest;
import com.fuoverflow.user.api.dto.UserProfileResponse;
import com.fuoverflow.user.persistence.UserMapper;
import com.fuoverflow.user.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserProfileService {
    private final UserRepository repository;
    private final UserMapper mapper;

    public UserProfileService(UserRepository repository, UserMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUser(UUID userId) {
        return repository.findById(userId)
                .filter(user -> user.getDeletedAt() == null)
                .map(mapper::toProfile)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateUserProfileRequest request) {
        var user = repository.findById(userId)
                .filter(entity -> entity.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        user.updateProfile(clean(request.displayName()), clean(request.firstName()), clean(request.lastName()), clean(request.avatarUrl()), Instant.now());
        return mapper.toProfile(user);
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
