package com.dearusback.voice.presentation.dto;

import com.dearusback.voice.domain.model.VoiceAction;
import com.dearusback.voice.domain.model.VoiceClientStatus;
import com.dearusback.voice.domain.model.VoiceRequest;
import java.time.Instant;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public record VoiceCommandResponse(Long voiceRequestId, VoiceClientStatus status, boolean pending,
                                   String transcript, VoiceAction action, JsonNode draft, Long recordId,
                                   String failureReason, Instant createdAt, Instant updatedAt) {
    public static VoiceCommandResponse from(VoiceRequest value, ObjectMapper mapper) {
        JsonNode draft = null;
        if (value.draftJson() != null) {
            try {
                draft = mapper.readTree(value.draftJson());
            } catch (RuntimeException ignored) {
                // A malformed provider draft is never reflected as raw text to the client.
            }
        }
        return new VoiceCommandResponse(value.id(), value.status(), value.status() == null,
                value.transcript(), value.action(), draft, value.recordId(), value.failureReason(),
                value.createdAt(), value.updatedAt());
    }
}
