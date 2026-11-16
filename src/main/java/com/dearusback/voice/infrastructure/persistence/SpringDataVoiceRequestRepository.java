package com.dearusback.voice.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

interface SpringDataVoiceRequestRepository extends JpaRepository<VoiceRequestJpaEntity, Long> {
    Optional<VoiceRequestJpaEntity> findByMemberIdAndIdempotencyKey(Long memberId, String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VoiceRequestJpaEntity v where v.id = :id")
    Optional<VoiceRequestJpaEntity> findLocked(@Param("id") Long id);

    @Modifying
    @Query(value = "UPDATE voice_request SET processing_status = 'FAILED', result_status = 'FAILED', "
            + "failure_reason = 'PROCESSING_INTERRUPTED', updated_at = :now "
            + "WHERE result_status IS NULL AND updated_at < :cutoff", nativeQuery = true)
    int failStale(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
