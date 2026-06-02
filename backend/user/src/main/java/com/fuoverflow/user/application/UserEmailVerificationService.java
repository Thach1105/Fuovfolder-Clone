package com.fuoverflow.user.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.persistence.UserMapper;
import com.fuoverflow.user.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserEmailVerificationService {
    private final UserRepository repository;
    private final UserMapper mapper;

    public UserEmailVerificationService(UserRepository repository, UserMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public AuthUserView markEmailVerified(UUID userId, Instant at) {
        var user = repository.findById(userId)
                .filter(entity -> entity.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        user.markEmailVerified(at);
        return mapper.toAuthUser(user);
    }
}
