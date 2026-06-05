package com.fuoverflow.user.persistence;

import java.util.UUID;

public interface UserEmailProjection {
    UUID getId();

    String getEmail();

    String getDisplayName();
}
