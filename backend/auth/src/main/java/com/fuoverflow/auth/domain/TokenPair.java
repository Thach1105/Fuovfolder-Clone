package com.fuoverflow.auth.domain;
import java.time.Instant;import java.util.UUID;
public record TokenPair(String accessToken,String refreshToken,String refreshTokenHash,UUID accessTokenJti,UUID refreshTokenJti,Instant issuedAt,Instant accessExpiresAt,Instant refreshExpiresAt){}