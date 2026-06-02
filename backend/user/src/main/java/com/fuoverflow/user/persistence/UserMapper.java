package com.fuoverflow.user.persistence;

import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.api.dto.UserProfileResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserMapper {
    public AuthUserView toAuthUser(UserEntity entity) {
        return new AuthUserView(
                entity.getId(),
                entity.getEmail(),
                entity.getUsername(),
                entity.getPasswordHash(),
                entity.getDisplayName(),
                entity.getStatus(),
                roles(entity.getRolesJson()),
                entity.getEmailVerifiedAt(),
                entity.getPasswordChangedAt(),
                entity.getDeletedAt()
        );
    }

    public UserProfileResponse toProfile(UserEntity entity) {
        return new UserProfileResponse(
                entity.getId(),
                entity.getEmail(),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getAvatarUrl(),
                entity.getStatus(),
                roles(entity.getRolesJson()),
                entity.getEmailVerifiedAt() != null,
                entity.getCreatedAt()
        );
    }

    private List<String> roles(String rolesJson) {
        if (rolesJson == null || rolesJson.isBlank()) return List.of("USER");
        return rolesJson.replace("[", "").replace("]", "").replace("\"", "").lines()
                .flatMap(line -> List.of(line.split(",")).stream())
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .toList();
    }
}
