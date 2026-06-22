package com.fuoverflow.auth.domain;

public record ProviderProfile(
        String provider,
        String providerUserId,
        String email,
        boolean emailVerified,
        String displayName,
        String avatarUrl
) {
}
