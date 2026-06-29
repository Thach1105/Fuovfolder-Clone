package com.fuoverflow.user.support;

import com.fuoverflow.common.broadcast.UserDisplayNameLookup;
import com.fuoverflow.user.persistence.UserEntity;
import com.fuoverflow.user.persistence.UserRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JpaUserDisplayNameLookup implements UserDisplayNameLookup {

    private final UserRepository userRepository;

    public JpaUserDisplayNameLookup(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public String getDisplayName(UUID userId) {
        return userRepository.findById(userId)
                .map(UserEntity::getDisplayName)
                .orElse("Một thành viên");
    }
}
