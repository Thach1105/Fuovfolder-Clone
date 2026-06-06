package com.fuoverflow.user.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.user.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserPasswordService {
    private final UserRepository repository;

    public UserPasswordService(UserRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void updatePassword(UUID userId, String passwordHash, Instant at) {
        var user = repository.findById(userId)
                .filter(entity -> entity.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        user.updatePassword(passwordHash, at);
    }
}
