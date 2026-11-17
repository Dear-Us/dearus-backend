package com.dearusback.voice.infrastructure.persistence;

import com.dearusback.voice.domain.model.VoiceAction;
import com.dearusback.voice.domain.model.VoiceClientStatus;
import com.dearusback.voice.domain.model.VoiceProcessingStatus;
import com.dearusback.voice.domain.model.VoiceRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "voice_request")
class VoiceRequestJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "voice_request_id")
    private Long id;
    @Column(name = "baby_id", nullable = false)
    private Long babyId;
    @Column(name = "requester_member_id", nullable = false)
    private Long memberId;
    @Column(name = "idempotency_key", nullable = false, length = 36)
    private String idempotencyKey;
    @Column(name = "payload_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String payloadHash;
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private VoiceProcessingStatus processingStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", length = 24)
    private VoiceClientStatus status;
    @Column(name = "transcript", columnDefinition = "TEXT")
    private String transcript;
    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 32)
    private VoiceAction action;
    @Column(name = "draft_json", columnDefinition = "TEXT")
    private String draftJson;
    @Column(name = "care_record_id")
    private Long recordId;
    @Column(name = "failure_reason", length = 40)
    private String failureReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VoiceRequestJpaEntity() { }

    VoiceRequestJpaEntity(VoiceRequest request) {
        babyId = request.babyId();
        memberId = request.memberId();
        idempotencyKey = request.idempotencyKey();
        payloadHash = request.payloadHash();
        apply(request);
        createdAt = request.createdAt();
    }

    VoiceRequest toDomain() {
        return new VoiceRequest(id, babyId, memberId, idempotencyKey, payloadHash,
                processingStatus, status, transcript, action, draftJson, recordId, failureReason,
                createdAt, updatedAt);
    }

    void apply(VoiceRequest request) {
        processingStatus = request.processingStatus();
        status = request.status();
        transcript = request.transcript();
        action = request.action();
        draftJson = request.draftJson();
        recordId = request.recordId();
        failureReason = request.failureReason();
        updatedAt = request.updatedAt();
    }
}
