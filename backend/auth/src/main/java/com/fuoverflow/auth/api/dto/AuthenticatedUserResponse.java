package com.fuoverflow.auth.api.dto;
import com.fuoverflow.user.domain.UserStatus;import java.util.UUID;
public record AuthenticatedUserResponse(UUID id,String email,String username,String displayName,UserStatus status,boolean emailVerified){}