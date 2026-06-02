package com.fuoverflow.auth.persistence;
import org.springframework.data.jpa.repository.JpaRepository;import java.util.Optional;import java.util.UUID;
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationTokenEntity,UUID>{ Optional<EmailVerificationTokenEntity> findByTokenHash(String tokenHash); }
