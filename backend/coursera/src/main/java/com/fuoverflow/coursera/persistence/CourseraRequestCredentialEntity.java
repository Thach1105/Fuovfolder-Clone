package com.fuoverflow.coursera.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coursera_request_credentials")
public class CourseraRequestCredentialEntity {
    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "account_label", length = 120)
    private String accountLabel;

    @Column(name = "coursera_email", nullable = false)
    private String courseraEmail;

    @Column(name = "password_ciphertext", nullable = false, columnDefinition = "text")
    private String passwordCiphertext;

    @Column(name = "password_key_id", nullable = false, length = 64)
    private String passwordKeyId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getCourseraEmail() { return courseraEmail; }
    public String getPasswordCiphertext() { return passwordCiphertext; }
    public String getPasswordKeyId() { return passwordKeyId; }

    public static CourseraRequestCredentialEntity create(
            UUID id, UUID requestId, String email, String ciphertext, String keyId, Instant now) {
        CourseraRequestCredentialEntity e = new CourseraRequestCredentialEntity();
        e.id = id;
        e.requestId = requestId;
        e.courseraEmail = email;
        e.passwordCiphertext = ciphertext;
        e.passwordKeyId = keyId;
        e.createdAt = now;
        return e;
    }
}
