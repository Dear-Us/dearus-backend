package com.dearusback.identity.infrastructure.persistence;

import com.dearusback.identity.application.port.out.RefreshSessionRepository;
import java.time.Instant;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshSessionPersistenceAdapter implements RefreshSessionRepository {

    private final SpringDataRefreshTokenRepository repository;

    public RefreshSessionPersistenceAdapter(SpringDataRefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(Long memberId, String tokenHash, Instant expiresAt, Instant createdAt) {
        repository.save(new RefreshTokenJpaEntity(memberId, tokenHash, expiresAt, createdAt));
    }
}
