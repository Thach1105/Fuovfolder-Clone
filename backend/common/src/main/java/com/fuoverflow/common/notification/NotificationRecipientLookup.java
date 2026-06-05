package com.fuoverflow.common.notification;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationRecipientLookup {
    List<Recipient> findEmailEligible(Collection<UUID> userIds);

    record Recipient(UUID userId, String email, String displayName) {
    }
}
