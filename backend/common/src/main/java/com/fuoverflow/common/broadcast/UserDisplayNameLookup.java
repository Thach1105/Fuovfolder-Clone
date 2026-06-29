package com.fuoverflow.common.broadcast;

import java.util.UUID;

public interface UserDisplayNameLookup {
    String getDisplayName(UUID userId);
}
