package com.fuoverflow.auth.api.dto;
import java.time.Instant;import java.util.List;
public record TokenIntrospectionResponse(boolean active,String tokenType,String subject,String username,String sessionId,String jti,Instant issuedAt,Instant notBefore,Instant expiresAt,Instant revokedAt,String revokedReason,List<String> roles,String reason){}