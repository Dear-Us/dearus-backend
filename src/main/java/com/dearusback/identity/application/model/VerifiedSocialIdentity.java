package com.dearusback.identity.application.model;

import com.dearusback.identity.domain.type.SocialProvider;
import java.util.Objects;

public record VerifiedSocialIdentity(
        SocialProvider provider,
        String providerUserId,
        String email,
        String displayName,
        String profileImageUrl
) {

    public VerifiedSocialIdentity {
        Objects.requireNonNull(provider, "provider must not be null");
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new IllegalArgumentException("providerUserId must not be blank");
        }
    }
}
