package com.dearusback.identity.application.port.out;

import java.time.Instant;

public interface RefreshSessionRepository {

    void save(Long memberId, String tokenHash, Instant expiresAt, Instant createdAt);
}
