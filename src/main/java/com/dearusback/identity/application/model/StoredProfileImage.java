package com.dearusback.identity.application.model;

import java.util.Objects;

public record StoredProfileImage(String url) {

    public StoredProfileImage {
        Objects.requireNonNull(url, "url must not be null");
        if (url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
    }
}
