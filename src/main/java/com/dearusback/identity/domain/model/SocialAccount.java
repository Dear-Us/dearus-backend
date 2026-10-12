package com.dearusback.identity.domain.model;

import com.dearusback.identity.domain.type.SocialProvider;
import java.util.Objects;

public final class SocialAccount {

    private final Long id;
    private final SocialProvider provider;
    private final String providerUserId;
    private String providerEmail;

    public SocialAccount(
            Long id,
            SocialProvider provider,
            String providerUserId,
            String providerEmail
    ) {
        this.id = id;
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.providerUserId = requireText(providerUserId, "providerUserId");
        this.providerEmail = providerEmail;
    }

    public static SocialAccount create(
            SocialProvider provider,
            String providerUserId,
            String providerEmail
    ) {
        return new SocialAccount(null, provider, providerUserId, providerEmail);
    }

    public void updateProviderEmail(String providerEmail) {
        if (providerEmail != null && !providerEmail.isBlank()) {
            this.providerEmail = providerEmail;
        }
    }

    public Long id() {
        return id;
    }

    public SocialProvider provider() {
        return provider;
    }

    public String providerUserId() {
        return providerUserId;
    }

    public String providerEmail() {
        return providerEmail;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
