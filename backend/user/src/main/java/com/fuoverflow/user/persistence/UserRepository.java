package com.fuoverflow.user.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByNormalizedEmailAndDeletedAtIsNull(String normalizedEmail);

    Optional<UserEntity> findByUsernameNormalizedAndDeletedAtIsNull(String usernameNormalized);

    boolean existsByNormalizedEmailAndDeletedAtIsNull(String normalizedEmail);

    boolean existsByUsernameNormalizedAndDeletedAtIsNull(String usernameNormalized);
}
