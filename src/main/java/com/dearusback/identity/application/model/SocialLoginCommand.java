package com.dearusback.identity.application.model;

import com.dearusback.identity.domain.type.SocialProvider;
import java.util.Objects;

public record SocialLoginCommand(SocialProvider provider, String providerToken) {

    public SocialLoginCommand {
        Objects.requireNonNull(provider, "provider must not be null");
        if (providerToken == null || providerToken.isBlank()) {
            throw new IllegalArgumentException("providerToken must not be blank");
        }
    }
}
