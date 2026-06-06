package com.fuoverflow.user.persistence;

import com.fuoverflow.user.api.dto.AdminUserSummaryResponse;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.api.dto.EffectivePermissions;
import com.fuoverflow.user.api.dto.UserProfileResponse;
import com.fuoverflow.user.application.PermissionResolverService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserMapper {
    private final PermissionResolverService permissionResolver;

    public UserMapper(PermissionResolverService permissionResolver) {
        this.permissionResolver = permissionResolver;
    }

    public AuthUserView toAuthUser(UserEntity entity) {
        EffectivePermissions effective = permissionResolver.resolve(entity.getId());
        return new AuthUserView(
                entity.getId(),
                entity.getEmail(),
                entity.getUsername(),
                entity.getPasswordHash(),
                entity.getDisplayName(),
                entity.getStatus(),
                effective.roles(),
                effective.permVersion(),
                effective.permissions(),
                effective.superAdmin(),
                entity.getEmailVerifiedAt(),
                entity.getPasswordChangedAt(),
                entity.getDeletedAt()
        );
    }

    public AdminUserSummaryResponse toAdminSummary(UserEntity entity) {
        EffectivePermissions effective = permissionResolver.resolve(entity.getId());
        return new AdminUserSummaryResponse(
                entity.getId(),
                entity.getEmail(),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getStatus(),
                effective.roles(),
                entity.getEmailVerifiedAt() != null,
                entity.getCreatedAt(),
                entity.getLastLoginAt()
        );
    }

    public UserProfileResponse toProfile(UserEntity entity) {
        EffectivePermissions effective = permissionResolver.resolve(entity.getId());
        return new UserProfileResponse(
                entity.getId(),
                entity.getEmail(),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getAvatarUrl(),
                entity.getStatus(),
                effective.roles(),
                effective.permVersion(),
                effective.permissions(),
                effective.superAdmin(),
                entity.getEmailVerifiedAt() != null,
                entity.getCreatedAt()
        );
    }
}
