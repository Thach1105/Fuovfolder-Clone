package com.fuoverflow.user.persistence;

import com.fuoverflow.user.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByNormalizedEmailAndDeletedAtIsNull(String normalizedEmail);

    Optional<UserEntity> findByUsernameNormalizedAndDeletedAtIsNull(String usernameNormalized);

    boolean existsByNormalizedEmailAndDeletedAtIsNull(String normalizedEmail);

    boolean existsByUsernameNormalizedAndDeletedAtIsNull(String usernameNormalized);

    Page<UserEntity> findByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    long countByDeletedAtIsNull();

    long countByDeletedAtIsNullAndStatus(UserStatus status);

    @Query(value = """
            select count(*) from users
            where deleted_at is null and roles_json @> cast(:roleJson as jsonb)
            """, nativeQuery = true)
    long countByRole(@Param("roleJson") String roleJson);
}
