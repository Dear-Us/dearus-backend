package com.dearusback.identity.application.model;

import java.time.Duration;
import java.util.Objects;

public record AuthTokenPolicy(Duration refreshTokenTtl) {

    public AuthTokenPolicy {
        Objects.requireNonNull(refreshTokenTtl, "refreshTokenTtl must not be null");
        if (refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
            throw new IllegalArgumentException("refreshTokenTtl must be positive");
        }
    }
}
