package com.fuoverflow.auth.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;import java.util.UUID;

@Entity
@Table(name="user_sessions")
public class UserSessionEntity{
 @Id private UUID id;
 @Column(name="user_id",nullable=false) private UUID userId;
 @Column(name="refresh_token_hash",nullable=false) private String refreshTokenHash;
 @Column(name="refresh_token_family_id",nullable=false) private UUID refreshTokenFamilyId;
 @Column(name="refresh_token_jti",nullable=false) private UUID refreshTokenJti;
 @Column(name="access_token_jti") private UUID accessTokenJti;
 @Column(name="issued_at",nullable=false) private Instant issuedAt;
 @Column(name="access_expires_at",nullable=false) private Instant accessExpiresAt;
 @Column(name="refresh_expires_at",nullable=false) private Instant refreshExpiresAt;
 @Column(name="last_used_at") private Instant lastUsedAt;
 @Column(name="revoked_at") private Instant revokedAt;
 @Column(name="revoked_reason") private String revokedReason;
 @Column(name="replaced_by_session_id") private UUID replacedBySessionId;
 @Column(name="ip_address", columnDefinition="inet") private String ipAddress;
 @Column(name="user_agent") private String userAgent;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="metadata_json", columnDefinition="jsonb", nullable=false) private String metadataJson="{}";
 @Version private long version;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 public static UserSessionEntity create(UUID id,UUID userId,String hash,UUID familyId,UUID refreshJti,UUID accessJti,Instant issuedAt,Instant accessExp,Instant refreshExp,String ip,String ua){var e=new UserSessionEntity();e.id=id;e.userId=userId;e.refreshTokenHash=hash;e.refreshTokenFamilyId=familyId;e.refreshTokenJti=refreshJti;e.accessTokenJti=accessJti;e.issuedAt=issuedAt;e.accessExpiresAt=accessExp;e.refreshExpiresAt=refreshExp;e.createdAt=issuedAt;e.ipAddress=ip;e.userAgent=ua;return e;}
 public UUID getId(){return id;} public UUID getUserId(){return userId;} public String getRefreshTokenHash(){return refreshTokenHash;} public UUID getRefreshTokenFamilyId(){return refreshTokenFamilyId;} public UUID getRefreshTokenJti(){return refreshTokenJti;} public UUID getAccessTokenJti(){return accessTokenJti;} public Instant getIssuedAt(){return issuedAt;} public Instant getAccessExpiresAt(){return accessExpiresAt;} public Instant getRefreshExpiresAt(){return refreshExpiresAt;} public Instant getLastUsedAt(){return lastUsedAt;} public Instant getRevokedAt(){return revokedAt;} public String getRevokedReason(){return revokedReason;}
 public boolean activeAt(Instant now){return revokedAt==null && refreshExpiresAt.isAfter(now);} public void markUsed(Instant now){lastUsedAt=now;} public void revoke(String reason,Instant now){revokedAt=now;revokedReason=reason;} public void replaceWith(UUID replacement,String reason,Instant now){replacedBySessionId=replacement;revoke(reason,now);} }
