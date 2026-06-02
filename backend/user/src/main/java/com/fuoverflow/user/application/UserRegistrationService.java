package com.fuoverflow.user.application;

import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.persistence.UserEntity;
import com.fuoverflow.user.persistence.UserMapper;
import com.fuoverflow.user.persistence.UserRepository;
import com.fuoverflow.user.validation.EmailNormalizer;
import com.fuoverflow.user.validation.UsernameNormalizer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserRegistrationService {
    private final UserRepository repository;
    private final UserMapper mapper;
    private final EmailNormalizer emailNormalizer;
    private final UsernameNormalizer usernameNormalizer;

    public UserRegistrationService(UserRepository repository, UserMapper mapper, EmailNormalizer emailNormalizer,
                                   UsernameNormalizer usernameNormalizer) {
        this.repository = repository;
        this.mapper = mapper;
        this.emailNormalizer = emailNormalizer;
        this.usernameNormalizer = usernameNormalizer;
    }

    @Transactional
    public AuthUserView register(RegisterUserCommand command) {
        String normalizedEmail = emailNormalizer.normalize(command.email());
        String normalizedUsername = usernameNormalizer.normalize(command.username());
        if (repository.existsByNormalizedEmailAndDeletedAtIsNull(normalizedEmail)) {
            throw new ConflictException("EMAIL_TAKEN", "Email is already used");
        }
        if (repository.existsByUsernameNormalizedAndDeletedAtIsNull(normalizedUsername)) {
            throw new ConflictException("USERNAME_TAKEN", "Username is already used");
        }
        Instant now = Instant.now();
        String campus = command.campus() == null || command.campus().isBlank() ? null : command.campus().trim();
        UserEntity entity = UserEntity.pending(UUID.randomUUID(), command.email().trim(), normalizedEmail,
                command.username().trim(), normalizedUsername, command.passwordHash(), command.displayName().trim(), campus, now);
        try {
            return mapper.toAuthUser(repository.save(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("USER_ALREADY_EXISTS", "Email or username is already used");
        }
    }
}
