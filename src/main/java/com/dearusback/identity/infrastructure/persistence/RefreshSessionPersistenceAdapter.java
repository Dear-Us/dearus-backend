package com.dearusback.identity.infrastructure.persistence;

import com.dearusback.identity.application.port.out.RefreshSessionRepository;
import com.dearusback.identity.domain.model.RefreshSession;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshSessionPersistenceAdapter implements RefreshSessionRepository {

    private final SpringDataRefreshTokenRepository repository;

    public RefreshSessionPersistenceAdapter(SpringDataRefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<RefreshSession> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash)
                .map(token -> new RefreshSession(
                        token.id(), token.memberId(), token.expiresAt(), token.revokedAt()
                ));
    }

    @Override
    public boolean revokeIfActive(Long sessionId, Instant now) {
        return repository.revokeIfActive(sessionId, now) == 1;
    }

    @Override
    public void save(Long memberId, String tokenHash, Instant expiresAt, Instant createdAt) {
        repository.saveAndFlush(new RefreshTokenJpaEntity(memberId, tokenHash, expiresAt, createdAt));
    }
}
