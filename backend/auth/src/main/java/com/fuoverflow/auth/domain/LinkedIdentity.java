package com.fuoverflow.auth.domain;

import java.util.UUID;

public record LinkedIdentity(
        UUID userId,
        boolean isNewUser,
        boolean isLinkedToExisting
) {
}
