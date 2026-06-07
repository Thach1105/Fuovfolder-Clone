package com.fuoverflow.user.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.common.storage.ObjectStorage;
import com.fuoverflow.material.application.UploadService;
import com.fuoverflow.material.domain.UploadPurpose;
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
    private final ObjectStorage objectStorage;
    private final UploadService uploadService;

    public UserProfileService(
            UserRepository repository,
            UserMapper mapper,
            ObjectStorage objectStorage,
            UploadService uploadService) {
        this.repository = repository;
        this.mapper = mapper;
        this.objectStorage = objectStorage;
        this.uploadService = uploadService;
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
        String nextAvatar = clean(request.avatarUrl());
        String previousAvatar = user.getAvatarUrl();
        if (nextAvatar != null && !nextAvatar.equals(previousAvatar)) {
            String normalized = objectStorage.normalizeToObjectKey(nextAvatar);
            uploadService.markLinkedByObjectKey(normalized, userId, UploadPurpose.AVATAR);
            nextAvatar = normalized;
            if (previousAvatar != null && !previousAvatar.equals(nextAvatar)) {
                objectStorage.delete(previousAvatar);
            }
        }
        user.updateProfile(
                clean(request.displayName()),
                clean(request.firstName()),
                clean(request.lastName()),
                nextAvatar,
                Instant.now());
        return mapper.toProfile(user);
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
