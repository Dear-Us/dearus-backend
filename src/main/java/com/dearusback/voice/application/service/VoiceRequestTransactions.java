package com.dearusback.voice.application.service;

import com.dearusback.family.application.port.in.ActiveBabyMembershipAccess;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import com.dearusback.voice.application.port.out.VoiceCareCommandPort;
import com.dearusback.voice.application.port.out.VoiceRequestRepository;
import com.dearusback.voice.domain.exception.VoiceErrorCode;
import com.dearusback.voice.domain.exception.VoiceException;
import com.dearusback.voice.domain.model.ParsedVoiceCommand;
import com.dearusback.voice.domain.model.VoiceAction;
import com.dearusback.voice.domain.model.VoiceClientStatus;
import com.dearusback.voice.domain.model.VoiceProcessingStatus;
import com.dearusback.voice.domain.model.VoiceRequest;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoiceRequestTransactions {
    private final VoiceRequestRepository requests;
    private final VoiceCareCommandPort care;
    private final ActiveBabyMembershipAccess babies;
    private final BabyRegistrationMemberAccess members;
    private final Clock clock;

    public VoiceRequestTransactions(VoiceRequestRepository requests, VoiceCareCommandPort care,
                                    ActiveBabyMembershipAccess babies, BabyRegistrationMemberAccess members,
                                    Clock clock) {
        this.requests = requests; this.care = care; this.babies = babies; this.members = members; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public void authorize(Long memberId, Long babyId) {
        members.requireActiveMember(memberId);
        babies.requireActiveMembership(memberId, babyId);
    }

    @Transactional
    public Registration register(Long memberId, Long babyId, String key, String hash) {
        String timezone = members.requireActiveMember(memberId).timezone();
        babies.requireActiveMembership(memberId, babyId);
        VoiceRequest existing = requests.findByMemberAndKey(memberId, key).orElse(null);
        if (existing != null) {
            if (!Objects.equals(existing.babyId(), babyId) || !existing.payloadHash().equals(hash)) {
                throw new VoiceException(VoiceErrorCode.REQUEST_CONFLICT);
            }
            return new Registration(existing, timezone, false);
        }
        VoiceRequest saved = requests.save(VoiceRequest.received(babyId, memberId, key, hash, clock.instant()));
        return new Registration(saved, timezone, true);
    }

    @Transactional(readOnly = true)
    public VoiceRequest existing(Long memberId, Long babyId, String key, String hash) {
        members.requireActiveMember(memberId);
        babies.requireActiveMembership(memberId, babyId);
        VoiceRequest value = requests.findByMemberAndKey(memberId, key)
                .orElseThrow(() -> new VoiceException(VoiceErrorCode.REQUEST_CONFLICT));
        if (!value.babyId().equals(babyId) || !value.payloadHash().equals(hash)) {
            throw new VoiceException(VoiceErrorCode.REQUEST_CONFLICT);
        }
        return value;
    }

    @Transactional(readOnly = true)
    public VoiceRequest get(Long memberId, Long babyId, Long id) {
        members.requireActiveMember(memberId);
        babies.requireActiveMembership(memberId, babyId);
        return owned(requests.findById(id).orElse(null), memberId, babyId);
    }

    @Transactional(readOnly = true)
    public boolean canProcess(Long id) {
        return requests.findById(id).map(value -> value.status() == null).orElse(false);
    }

    @Transactional
    public boolean transcribed(Long id, String text) {
        VoiceRequest value = requests.findByIdForUpdate(id).orElseThrow();
        if (value.processingStatus() != VoiceProcessingStatus.RECEIVED) return false;
        requests.save(value.transcribed(text, clock.instant()));
        return true;
    }

    @Transactional
    public boolean parsed(Long id, ParsedVoiceCommand command) {
        VoiceRequest value = requests.findByIdForUpdate(id).orElseThrow();
        if (value.processingStatus() != VoiceProcessingStatus.TRANSCRIBED) return false;
        requests.save(value.parsed(command, clock.instant()));
        return true;
    }

    @Transactional
    public void finish(Long id, ParsedVoiceCommand command) {
        VoiceRequest value = requests.findByIdForUpdate(id).orElseThrow();
        if (value.processingStatus() != VoiceProcessingStatus.PARSED) return;
        VoiceCareCommandPort.Validation validation = command.action() == null
                ? new VoiceCareCommandPort.Validation(false, false)
                : care.validate(value.babyId(), value.memberId(), command.action(), command.draftJson());
        if (command.ambiguous() || command.multipleRecords() || !validation.valid()) {
            requests.save(value.pending(VoiceClientStatus.REVIEW_REQUIRED, clock.instant()));
        } else if (validation.medicine()) {
            requests.save(value.pending(VoiceClientStatus.CONFIRMATION_REQUIRED, clock.instant()));
        } else {
            Long recordId = care.execute(value.babyId(), value.memberId(), command.action(), command.draftJson());
            requests.save(value.autoSaved(recordId, clock.instant()));
        }
    }

    @Transactional
    public void fail(Long id, String safeReason) {
        VoiceRequest value = requests.findByIdForUpdate(id).orElse(null);
        if (value != null && value.status() == null) {
            requests.save(value.failed(safeReason, clock.instant()));
        }
    }

    @Transactional
    public VoiceRequest confirm(Long memberId, Long babyId, Long id, VoiceAction chosenAction, String chosenDraft) {
        members.requireActiveMember(memberId);
        babies.requireActiveMembership(memberId, babyId);
        VoiceRequest value = owned(requests.findByIdForUpdate(id).orElse(null), memberId, babyId);
        if (value.status() == VoiceClientStatus.CONFIRMED) return value;
        if (value.status() != VoiceClientStatus.CONFIRMATION_REQUIRED
                && value.status() != VoiceClientStatus.REVIEW_REQUIRED) {
            throw new VoiceException(VoiceErrorCode.REQUEST_CONFLICT);
        }
        if (value.status() == VoiceClientStatus.CONFIRMATION_REQUIRED
                && (chosenAction != null || chosenDraft != null)) {
            throw new VoiceException(VoiceErrorCode.INVALID_REQUEST);
        }
        VoiceAction action = chosenAction == null ? value.action() : chosenAction;
        String draft = chosenDraft == null ? value.draftJson() : chosenDraft;
        if (action == null || !care.validate(babyId, memberId, action, draft).valid()) {
            throw new VoiceException(VoiceErrorCode.INVALID_REQUEST);
        }
        Long recordId = care.execute(babyId, memberId, action, draft);
        return requests.save(value.confirmed(action, draft, recordId, clock.instant()));
    }

    @Transactional
    public VoiceRequest cancel(Long memberId, Long babyId, Long id) {
        members.requireActiveMember(memberId);
        babies.requireActiveMembership(memberId, babyId);
        VoiceRequest value = owned(requests.findByIdForUpdate(id).orElse(null), memberId, babyId);
        if (value.status() == VoiceClientStatus.CANCELED) return value;
        try {
            return requests.save(value.canceled(clock.instant()));
        } catch (IllegalStateException exception) {
            throw new VoiceException(VoiceErrorCode.REQUEST_CONFLICT);
        }
    }

    private VoiceRequest owned(VoiceRequest value, Long memberId, Long babyId) {
        if (value == null || !value.memberId().equals(memberId) || !value.babyId().equals(babyId)) {
            throw new VoiceException(VoiceErrorCode.REQUEST_NOT_FOUND);
        }
        return value;
    }

    public record Registration(VoiceRequest request, String timezone, boolean created) { }
}
