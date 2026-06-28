package com.fuoverflow.user.persistence;

import com.fuoverflow.user.domain.UserRole;
import com.fuoverflow.user.domain.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(name = "username_normalized", nullable = false, length = 64)
    private String usernameNormalized;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "normalized_email", nullable = false, length = 320)
    private String normalizedEmail;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(length = 120)
    private String campus;

    @Column(name = "first_name", length = 80)
    private String firstName;

    @Column(name = "last_name", length = 80)
    private String lastName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private UserStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "roles_json", columnDefinition = "jsonb", nullable = false)
    private String rolesJson;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private int lockVersion;

    public static UserEntity pending(UUID id, String email, String normalizedEmail, String username, String usernameNormalized,
                                     String passwordHash, String displayName, String campus, Instant now) {
        UserEntity entity = new UserEntity();
        entity.id = id;
        entity.email = email;
        entity.normalizedEmail = normalizedEmail;
        entity.username = username;
        entity.usernameNormalized = usernameNormalized;
        entity.passwordHash = passwordHash;
        entity.displayName = displayName;
        entity.campus = campus;
        entity.status = UserStatus.PENDING_EMAIL_VERIFICATION;
        entity.emailVerified = false;
        entity.rolesJson = rolesJson(UserRole.USER);
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.lockVersion = 0;
        return entity;
    }

    public static UserEntity seededAdministrator(UUID id, String email, String normalizedEmail, String username,
                                                 String usernameNormalized, String passwordHash, String displayName,
                                                 Instant now) {
        UserEntity entity = new UserEntity();
        entity.id = id;
        entity.email = email;
        entity.normalizedEmail = normalizedEmail;
        entity.username = username;
        entity.usernameNormalized = usernameNormalized;
        entity.passwordHash = passwordHash;
        entity.displayName = displayName;
        entity.status = UserStatus.ACTIVE;
        entity.rolesJson = rolesJson(UserRole.SUPER_ADMIN);
        entity.emailVerifiedAt = now;
        entity.emailVerified = true;
        entity.passwordChangedAt = now;
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.lockVersion = 0;
        return entity;
    }

    public static UserEntity oauthUser(UUID id, String email, String normalizedEmail, String username, String usernameNormalized,
                                       String passwordHash, String displayName, String campus, boolean emailVerified,
                                       UserStatus status, Instant now) {
        UserEntity entity = new UserEntity();
        entity.id = id;
        entity.email = email;
        entity.normalizedEmail = normalizedEmail;
        entity.username = username;
        entity.usernameNormalized = usernameNormalized;
        entity.passwordHash = passwordHash;
        entity.displayName = displayName;
        entity.campus = campus;
        entity.emailVerified = emailVerified;
        if (emailVerified) {
            entity.emailVerifiedAt = now;
        }
        entity.status = status;
        entity.rolesJson = rolesJson(UserRole.USER);
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.lockVersion = 0;
        return entity;
    }

    public static String rolesJson(UserRole... roles) {
        if (roles == null || roles.length == 0) {
            return "[\"USER\"]";
        }
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < roles.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append('"').append(roles[i].name()).append('"');
        }
        return json.append(']').toString();
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getUsernameNormalized() { return usernameNormalized; }
    public String getEmail() { return email; }
    public String getNormalizedEmail() { return normalizedEmail; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getCampus() { return campus; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getAvatarUrl() { return avatarUrl; }
    public UserStatus getStatus() { return status; }
    public String getRolesJson() { return rolesJson; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public boolean isEmailVerified() { return emailVerified; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getPasswordChangedAt() { return passwordChangedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void markEmailVerified(Instant at) {
        this.emailVerifiedAt = at;
        this.emailVerified = true;
        this.status = UserStatus.ACTIVE;
        this.updatedAt = at;
    }

    public void updatePassword(String passwordHash, Instant at) {
        this.passwordHash = passwordHash;
        this.passwordChangedAt = at;
        this.updatedAt = at;
    }

    public void markLastLogin(Instant at) {
        this.lastLoginAt = at;
        this.updatedAt = at;
    }

    public void completeProfile(String username, String usernameNormalized, String displayName, String campus, Instant at) {
        this.username = username;
        this.usernameNormalized = usernameNormalized;
        this.displayName = displayName;
        this.campus = campus;
        this.status = UserStatus.ACTIVE;
        this.updatedAt = at;
    }

    public void updateProfile(String displayName, String firstName, String lastName, String avatarUrl, Instant at) {
        if (displayName != null) this.displayName = displayName;
        this.firstName = firstName;
        this.lastName = lastName;
        this.avatarUrl = avatarUrl;
        this.updatedAt = at;
    }

    public void markDeleted(Instant at) {
        this.status = UserStatus.DELETED;
        this.deletedAt = at;
        this.updatedAt = at;
    }
}
