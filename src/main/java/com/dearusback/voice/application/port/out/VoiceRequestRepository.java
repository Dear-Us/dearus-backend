package com.dearusback.voice.application.port.out;

import com.dearusback.voice.domain.model.VoiceRequest;
import java.util.Optional;

public interface VoiceRequestRepository {
    VoiceRequest save(VoiceRequest request);
    Optional<VoiceRequest> findById(Long id);
    Optional<VoiceRequest> findByIdForUpdate(Long id);
    Optional<VoiceRequest> findByMemberAndKey(Long memberId, String key);
    int failStaleProcessing(java.time.Instant updatedBefore, java.time.Instant now);
}
