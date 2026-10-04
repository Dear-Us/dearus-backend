package com.dearusback.identity.application.port.out;

import com.dearusback.identity.domain.model.RefreshSession;
import java.time.Instant;
import java.util.Optional;

public interface RefreshSessionRepository {

    Optional<RefreshSession> findByTokenHash(String tokenHash);

    boolean revokeIfActive(Long sessionId, Instant now);

    boolean revokeByTokenHash(String tokenHash, Instant revokedAt);

    void save(Long memberId, String tokenHash, Instant expiresAt, Instant createdAt);
}
