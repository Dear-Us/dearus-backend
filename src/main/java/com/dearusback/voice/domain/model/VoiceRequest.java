package com.dearusback.voice.domain.model;

import java.time.Instant;
import java.util.Objects;

public record VoiceRequest(Long id, Long babyId, Long memberId, String idempotencyKey, String payloadHash,
                           VoiceProcessingStatus processingStatus, VoiceClientStatus status, String transcript,
                           VoiceAction action, String draftJson, Long recordId, String failureReason,
                           Instant createdAt, Instant updatedAt) {
    public VoiceRequest {
        Objects.requireNonNull(babyId);
        Objects.requireNonNull(memberId);
        Objects.requireNonNull(idempotencyKey);
        Objects.requireNonNull(payloadHash);
        Objects.requireNonNull(processingStatus);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    public static VoiceRequest received(Long babyId, Long memberId, String key, String hash, Instant now) {
        return new VoiceRequest(null, babyId, memberId, key, hash, VoiceProcessingStatus.RECEIVED,
                null, null, null, null, null, null, now, now);
    }

    public VoiceRequest withId(Long value) {
        return new VoiceRequest(value, babyId, memberId, idempotencyKey, payloadHash, processingStatus,
                status, transcript, action, draftJson, recordId, failureReason, createdAt, updatedAt);
    }

    public VoiceRequest transcribed(String text, Instant now) {
        require(VoiceProcessingStatus.RECEIVED);
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Empty transcript");
        return next(VoiceProcessingStatus.TRANSCRIBED, null, text.trim(), null, null, null, null, now);
    }

    public VoiceRequest parsed(ParsedVoiceCommand command, Instant now) {
        require(VoiceProcessingStatus.TRANSCRIBED);
        Objects.requireNonNull(command);
        return next(VoiceProcessingStatus.PARSED, null, transcript, command.action(),
                command.draftJson(), null, null, now);
    }

    public VoiceRequest pending(VoiceClientStatus result, Instant now) {
        require(VoiceProcessingStatus.PARSED);
        if (result != VoiceClientStatus.REVIEW_REQUIRED && result != VoiceClientStatus.CONFIRMATION_REQUIRED) {
            throw new IllegalStateException("Invalid pending result");
        }
        return next(VoiceProcessingStatus.COMPLETED, result, transcript, action, draftJson, null, null, now);
    }

    public VoiceRequest autoSaved(Long careRecordId, Instant now) {
        require(VoiceProcessingStatus.PARSED);
        return next(VoiceProcessingStatus.COMPLETED, VoiceClientStatus.AUTO_SAVED,
                transcript, action, draftJson, Objects.requireNonNull(careRecordId), null, now);
    }

    public VoiceRequest confirmed(VoiceAction selectedAction, String selectedDraft, Long careRecordId, Instant now) {
        if (status != VoiceClientStatus.REVIEW_REQUIRED && status != VoiceClientStatus.CONFIRMATION_REQUIRED) {
            throw new IllegalStateException("Voice request cannot be confirmed");
        }
        return next(VoiceProcessingStatus.COMPLETED, VoiceClientStatus.CONFIRMED, transcript,
                Objects.requireNonNull(selectedAction), Objects.requireNonNull(selectedDraft),
                Objects.requireNonNull(careRecordId), null, now);
    }

    public VoiceRequest failed(String reason, Instant now) {
        if (processingStatus != VoiceProcessingStatus.RECEIVED
                && processingStatus != VoiceProcessingStatus.TRANSCRIBED
                && processingStatus != VoiceProcessingStatus.PARSED) {
            throw new IllegalStateException("Voice request is terminal");
        }
        return next(VoiceProcessingStatus.FAILED, VoiceClientStatus.FAILED,
                transcript, action, draftJson, null, Objects.requireNonNull(reason), now);
    }

    public VoiceRequest canceled(Instant now) {
        if (processingStatus == VoiceProcessingStatus.FAILED || processingStatus == VoiceProcessingStatus.CANCELED
                || status == VoiceClientStatus.AUTO_SAVED || status == VoiceClientStatus.CONFIRMED) {
            throw new IllegalStateException("Voice request cannot be canceled");
        }
        return next(VoiceProcessingStatus.CANCELED, VoiceClientStatus.CANCELED,
                transcript, action, draftJson, null, null, now);
    }

    private void require(VoiceProcessingStatus expected) {
        if (processingStatus != expected) throw new IllegalStateException("Illegal voice state transition");
    }

    private VoiceRequest next(VoiceProcessingStatus processing, VoiceClientStatus client, String text,
                              VoiceAction newAction, String draft, Long careId, String reason, Instant now) {
        return new VoiceRequest(id, babyId, memberId, idempotencyKey, payloadHash, processing,
                client, text, newAction, draft, careId, reason, createdAt, now);
    }
}
