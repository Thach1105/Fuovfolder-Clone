package com.fuoverflow.user.application;

import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.persistence.UserMapper;
import com.fuoverflow.user.persistence.UserRepository;
import com.fuoverflow.user.validation.EmailNormalizer;
import com.fuoverflow.user.validation.UsernameNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserLookupService {
    private final UserRepository repository;
    private final UserMapper mapper;
    private final EmailNormalizer emailNormalizer;
    private final UsernameNormalizer usernameNormalizer;

    public UserLookupService(UserRepository repository, UserMapper mapper, EmailNormalizer emailNormalizer,
                             UsernameNormalizer usernameNormalizer) {
        this.repository = repository;
        this.mapper = mapper;
        this.emailNormalizer = emailNormalizer;
        this.usernameNormalizer = usernameNormalizer;
    }

    @Transactional(readOnly = true)
    public Optional<AuthUserView> findAuthUserByIdentifier(String identifier) {
        if (identifier != null && identifier.contains("@")) {
            return repository.findByNormalizedEmailAndDeletedAtIsNull(emailNormalizer.normalize(identifier)).map(mapper::toAuthUser);
        }
        try {
            return repository.findByUsernameNormalizedAndDeletedAtIsNull(usernameNormalizer.normalize(identifier)).map(mapper::toAuthUser);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Optional<AuthUserView> findAuthUserById(UUID userId) {
        return repository.findById(userId).filter(u -> u.getDeletedAt() == null).map(mapper::toAuthUser);
    }
}
