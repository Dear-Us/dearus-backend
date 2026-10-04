package com.dearusback.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

public record RefreshSession(
        Long id,
        Long memberId,
        Instant expiresAt,
        Instant revokedAt
) {

    public RefreshSession {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(memberId, "memberId must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public boolean canRotateAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
