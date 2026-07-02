package com.fuoverflow.auth.api.dto;
import java.time.Instant;import java.util.UUID;
public record AuthTokenResponse(String tokenType,Instant accessTokenExpiresAt,Instant refreshTokenExpiresAt,Instant issuedAt,AuthenticatedUserResponse user){}