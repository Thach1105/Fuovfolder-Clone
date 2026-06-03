package com.fuoverflow.user.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.user.persistence.UserEntity;
import com.fuoverflow.user.persistence.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserPasswordService {
    private final UserRepository repository;

    public UserPasswordService(UserRepository repository) {
        this.repository = repository;
    }

    public void updatePassword(UUID userId, String newPasswordHash) {
        UserEntity user = repository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        user.updatePassword(newPasswordHash, Instant.now());
        repository.save(user);
    }
}
