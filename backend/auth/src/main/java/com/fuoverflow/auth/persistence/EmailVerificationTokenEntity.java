package com.fuoverflow.auth.persistence;

import jakarta.persistence.*;import java.time.Instant;import java.util.UUID;
@Entity @Table(name="email_verification_tokens")
public class EmailVerificationTokenEntity{
 @Id private UUID id; @Column(name="user_id",nullable=false) private UUID userId; @Column(name="token_hash",nullable=false) private String tokenHash; @Column(name="expires_at",nullable=false) private Instant expiresAt; @Column(name="consumed_at") private Instant consumedAt; @Column(name="created_at",nullable=false) private Instant createdAt;
 public static EmailVerificationTokenEntity create(UUID id,UUID userId,String hash,Instant expiresAt,Instant now){var e=new EmailVerificationTokenEntity();e.id=id;e.userId=userId;e.tokenHash=hash;e.expiresAt=expiresAt;e.createdAt=now;return e;}
 public UUID getUserId(){return userId;} public Instant getExpiresAt(){return expiresAt;} public Instant getConsumedAt(){return consumedAt;} public void consume(Instant now){consumedAt=now;} public boolean activeAt(Instant now){return consumedAt==null && expiresAt.isAfter(now);} }
