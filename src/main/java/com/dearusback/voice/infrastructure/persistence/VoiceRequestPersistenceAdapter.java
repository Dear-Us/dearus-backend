package com.dearusback.voice.infrastructure.persistence;

import com.dearusback.voice.application.port.out.VoiceRequestRepository;
import com.dearusback.voice.domain.model.VoiceRequest;
import java.util.Optional;
import java.time.Instant;
import org.springframework.stereotype.Repository;

@Repository
class VoiceRequestPersistenceAdapter implements VoiceRequestRepository {
    private final SpringDataVoiceRequestRepository repository;

    VoiceRequestPersistenceAdapter(SpringDataVoiceRequestRepository repository) {
        this.repository = repository;
    }

    @Override public VoiceRequest save(VoiceRequest request) {
        if (request.id() == null) return repository.saveAndFlush(new VoiceRequestJpaEntity(request)).toDomain();
        VoiceRequestJpaEntity entity = repository.findById(request.id()).orElseThrow();
        entity.apply(request);
        return repository.saveAndFlush(entity).toDomain();
    }

    @Override public Optional<VoiceRequest> findById(Long id) {
        return repository.findById(id).map(VoiceRequestJpaEntity::toDomain);
    }

    @Override public Optional<VoiceRequest> findByIdForUpdate(Long id) {
        return repository.findLocked(id).map(VoiceRequestJpaEntity::toDomain);
    }

    @Override public Optional<VoiceRequest> findByMemberAndKey(Long memberId, String key) {
        return repository.findByMemberIdAndIdempotencyKey(memberId, key).map(VoiceRequestJpaEntity::toDomain);
    }

    @Override public int failStaleProcessing(Instant updatedBefore, Instant now) {
        return repository.failStale(updatedBefore, now);
    }
}
