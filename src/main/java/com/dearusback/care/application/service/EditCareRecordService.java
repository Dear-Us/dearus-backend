package com.dearusback.care.application.service;

import com.dearusback.care.application.model.EditCareRecordCommand;
import com.dearusback.care.application.model.RecordChangedEvent;
import com.dearusback.care.application.port.in.EditCareRecordUseCase;
import com.dearusback.care.application.port.out.RecordChangePublisher;
import com.dearusback.care.domain.exception.CareErrorCode;
import com.dearusback.care.domain.exception.CareException;
import com.dearusback.care.domain.model.CareRecord;
import com.dearusback.care.domain.model.CareRecord.CareRecordType;
import com.dearusback.care.domain.repository.CareRecordRepository;
import com.dearusback.family.application.port.in.ActiveBabyMembershipAccess;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EditCareRecordService implements EditCareRecordUseCase {

    private final BabyRegistrationMemberAccess members;
    private final ActiveBabyMembershipAccess babies;
    private final CareRecordRepository records;
    private final Clock clock;
    private final RecordChangePublisher changes;

    public EditCareRecordService(BabyRegistrationMemberAccess members, ActiveBabyMembershipAccess babies,
                                 CareRecordRepository records, Clock clock, RecordChangePublisher changes) {
        this.members = members;
        this.babies = babies;
        this.records = records;
        this.clock = clock;
        this.changes = changes;
    }

    @Override
    @Transactional
    public CareRecord edit(EditCareRecordCommand command) {
        ZoneId zone = ZoneId.of(members.requireActiveMember(command.memberId()).timezone());
        babies.requireActiveMembership(command.memberId(), command.babyId());
        CareRecord existing = find(command.babyId(), command.recordId());
        requireEditable(existing, command.version());
        if ((existing.recordType() == CareRecordType.SLEEP
                && (command.recordedAt() != null || command.detail() != null))
                || (existing.recordType() != CareRecordType.SLEEP
                && (command.startAt() != null || command.endAt() != null))) {
            throw new CareException(CareErrorCode.INVALID_RECORD);
        }
        CareRecord changed;
        try {
            changed = existing.edit(command.recordedAt(), command.startAt(), command.endAt(),
                    command.memoPresent(), command.memo(), command.detail(), command.memberId(), clock.instant());
        } catch (IllegalArgumentException exception) {
            throw new CareException(CareErrorCode.INVALID_RECORD);
        }
        try {
            CareRecord saved = records.updateCompleted(changed, command.detail() != null);
            changes.publish(RecordChangedEvent.from(RecordChangedEvent.EventType.RECORD_UPDATED, saved));
            return saved.inZone(zone);
        } catch (OptimisticLockingFailureException exception) {
            throw new CareException(CareErrorCode.RECORD_VERSION_CONFLICT);
        }
    }

    @Override
    @Transactional
    public void delete(Long memberId, Long babyId, Long recordId, long version) {
        members.requireActiveMember(memberId);
        babies.requireActiveMembership(memberId, babyId);
        CareRecord existing = find(babyId, recordId);
        requireEditable(existing, version);
        try {
            CareRecord saved = records.updateCompleted(existing.softDelete(memberId, clock.instant()), false);
            changes.publish(RecordChangedEvent.from(RecordChangedEvent.EventType.RECORD_DELETED, saved));
        } catch (OptimisticLockingFailureException exception) {
            throw new CareException(CareErrorCode.RECORD_VERSION_CONFLICT);
        }
    }

    private CareRecord find(Long babyId, Long recordId) {
        return records.findByBabyAndId(babyId, recordId)
                .orElseThrow(() -> new CareException(CareErrorCode.RECORD_NOT_FOUND));
    }

    private void requireEditable(CareRecord record, long version) {
        if (record.status() != CareRecord.CareRecordStatus.COMPLETED) {
            throw new CareException(CareErrorCode.RECORD_NOT_EDITABLE);
        }
        if (record.version() != version) {
            throw new CareException(CareErrorCode.RECORD_VERSION_CONFLICT);
        }
    }
}
