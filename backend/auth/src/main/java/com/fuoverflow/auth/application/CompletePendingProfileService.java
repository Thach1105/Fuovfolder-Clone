package com.fuoverflow.auth.application;

import com.fuoverflow.auth.api.dto.CompletePendingProfileRequest;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.user.api.dto.UserProfileResponse;
import com.fuoverflow.user.domain.UserStatus;
import com.fuoverflow.user.persistence.UserEntity;
import com.fuoverflow.user.persistence.UserMapper;
import com.fuoverflow.user.persistence.UserRepository;
import com.fuoverflow.user.validation.UsernameNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CompletePendingProfileService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UsernameNormalizer usernameNormalizer;

    public CompletePendingProfileService(UserRepository userRepository, UserMapper userMapper,
                                         UsernameNormalizer usernameNormalizer) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.usernameNormalizer = usernameNormalizer;
    }

    @Transactional
    public UserProfileResponse complete(UUID userId, CompletePendingProfileRequest request) {
        UserEntity user = userRepository.findById(userId)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        if (user.getStatus() != UserStatus.PENDING_PROFILE) {
            throw new ForbiddenException("NOT_PENDING_PROFILE", "Profile is already completed");
        }

        String normalizedUsername = usernameNormalizer.normalize(request.username());
        if (!normalizedUsername.equals(user.getUsernameNormalized())
                && userRepository.existsByUsernameNormalizedAndDeletedAtIsNull(normalizedUsername)) {
            throw new ConflictException("USERNAME_TAKEN", "Username is already used");
        }

        user.completeProfile(request.username().trim(), normalizedUsername,
                request.displayName().trim(), request.campus().trim(), Instant.now());

        return userMapper.toProfile(user);
    }
}
