package com.fuoverflow.user.persistence;

import com.fuoverflow.user.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByNormalizedEmailAndDeletedAtIsNull(String normalizedEmail);

    Optional<UserEntity> findByUsernameNormalizedAndDeletedAtIsNull(String usernameNormalized);

    boolean existsByIdAndDeletedAtIsNull(UUID id);

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

    @Query("""
            SELECT u.id AS id, u.email AS email, u.displayName AS displayName
            FROM UserEntity u
            WHERE u.id IN :userIds
              AND u.deletedAt IS NULL
              AND u.emailVerifiedAt IS NOT NULL
            """)
    List<UserEmailProjection> findEmailEligibleByIds(@Param("userIds") Collection<UUID> userIds);
}
