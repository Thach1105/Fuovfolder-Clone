package com.fuoverflow.user.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserOAuthAccountRepository extends JpaRepository<UserOAuthAccountEntity, UUID> {
    Optional<UserOAuthAccountEntity> findByProviderAndProviderUserId(String provider, String providerUserId);
    List<UserOAuthAccountEntity> findByUserId(UUID userId);
}
