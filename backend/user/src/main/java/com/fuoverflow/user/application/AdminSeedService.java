package com.fuoverflow.user.application;

import com.fuoverflow.user.config.AdminSeedProperties;
import com.fuoverflow.user.domain.UserRole;
import com.fuoverflow.user.persistence.UserEntity;
import com.fuoverflow.user.persistence.UserRepository;
import com.fuoverflow.user.validation.EmailNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Component
public class AdminSeedService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminSeedService.class);

    private final AdminSeedProperties properties;
    private final UserRepository repository;
    private final EmailNormalizer emailNormalizer;
    private final PasswordEncoder passwordEncoder;
    private final RoleAssignmentService roleAssignmentService;

    public AdminSeedService(AdminSeedProperties properties, UserRepository repository,
                            EmailNormalizer emailNormalizer, PasswordEncoder passwordEncoder,
                            RoleAssignmentService roleAssignmentService) {
        this.properties = properties;
        this.repository = repository;
        this.emailNormalizer = emailNormalizer;
        this.passwordEncoder = passwordEncoder;
        this.roleAssignmentService = roleAssignmentService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        String password = properties.password();
        if (password == null || password.isBlank()) {
            log.warn("Admin seed is enabled but fuoverflow.admin.seed.password is empty; skipping admin bootstrap");
            return;
        }
        if (password.length() < 8 || password.length() > 128) {
            log.warn("Admin seed password must be 8-128 characters; skipping admin bootstrap");
            return;
        }

        String email = properties.email().trim();
        String normalizedEmail = emailNormalizer.normalize(email);
        if (repository.existsByNormalizedEmailAndDeletedAtIsNull(normalizedEmail)) {
            log.debug("Admin seed skipped: user already exists for {}", normalizedEmail);
            return;
        }

        String username = properties.username().trim();
        String usernameNormalized = username.toLowerCase(Locale.ROOT);
        if (repository.existsByUsernameNormalizedAndDeletedAtIsNull(usernameNormalized)) {
            log.debug("Admin seed skipped: username already exists for {}", usernameNormalized);
            return;
        }

        Instant now = Instant.now();
        String displayName = properties.displayName() == null || properties.displayName().isBlank()
                ? "Administrator"
                : properties.displayName().trim();
        UserEntity admin = UserEntity.seededAdministrator(
                UUID.randomUUID(),
                email,
                normalizedEmail,
                username,
                usernameNormalized,
                passwordEncoder.encode(password),
                displayName,
                now);
        repository.save(admin);
        roleAssignmentService.assignGlobalRole(admin.getId(), "SUPER_ADMIN", null);
        roleAssignmentService.assignGlobalRole(admin.getId(), "USER", null);
        log.info("Seeded administrator account for email {}", normalizedEmail);
    }
}
