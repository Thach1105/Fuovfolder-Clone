package com.fuoverflow.user.application;

import com.fuoverflow.common.notification.NotificationRecipientLookup;
import com.fuoverflow.user.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationRecipientLookupService implements NotificationRecipientLookup {
    private final UserRepository userRepository;

    public NotificationRecipientLookupService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Recipient> findEmailEligible(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findEmailEligibleByIds(userIds).stream()
                .map(row -> new Recipient(row.getId(), row.getEmail(), row.getDisplayName()))
                .toList();
    }
}
