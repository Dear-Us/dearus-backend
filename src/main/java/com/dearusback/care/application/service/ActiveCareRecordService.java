package com.dearusback.care.application.service;

import com.dearusback.care.application.model.StartCareRecordCommand;
import com.dearusback.care.application.port.in.ActiveCareRecordUseCase;
import com.dearusback.care.application.port.out.CareRecordQueryPort;
import com.dearusback.care.domain.exception.CareErrorCode;
import com.dearusback.care.domain.exception.CareException;
import com.dearusback.care.domain.model.CareRecord;
import com.dearusback.care.domain.repository.CareRecordRepository;
import com.dearusback.family.application.port.in.ActiveBabyMembershipAccess;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActiveCareRecordService implements ActiveCareRecordUseCase {

    private final BabyRegistrationMemberAccess members;
    private final ActiveBabyMembershipAccess babies;
    private final CareRecordRepository records;
    private final CareRecordQueryPort queries;
    private final Clock clock;

    public ActiveCareRecordService(BabyRegistrationMemberAccess members, ActiveBabyMembershipAccess babies,
                                   CareRecordRepository records, CareRecordQueryPort queries, Clock clock) {
        this.members = members;
        this.babies = babies;
        this.records = records;
        this.queries = queries;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CareRecord start(StartCareRecordCommand command) {
        ZoneId zone = zone(command.memberId());
        babies.requireActiveMembershipForUpdate(command.memberId(), command.babyId());
        if (queries.findActiveRecord(command.babyId()).isPresent()) {
            throw new CareException(CareErrorCode.ACTIVE_RECORD_EXISTS);
        }
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), zone);
        CareRecord record;
        try {
            record = switch (command.recordType()) {
                case SLEEP -> {
                    if (command.breastSide() != null) throw new IllegalArgumentException();
                    yield CareRecord.startSleep(command.babyId(), command.memberId(), command.inputType(),
                            now, command.memo());
                }
                case FEEDING -> CareRecord.startBreastfeeding(command.babyId(), command.memberId(),
                        command.inputType(), now, command.memo(), command.breastSide());
                default -> throw new IllegalArgumentException();
            };
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new CareException(CareErrorCode.INVALID_RECORD);
        }
        try {
            return records.save(record);
        } catch (DataIntegrityViolationException exception) {
            if (activeUniqueViolation(exception)) {
                throw new CareException(CareErrorCode.ACTIVE_RECORD_EXISTS);
            }
            throw exception;
        }
    }

    @Override
    @Transactional
    public CareRecord complete(Long memberId, Long babyId, Long recordId) {
        ZoneId zone = zone(memberId);
        babies.requireActiveMembership(memberId, babyId);
        CareRecord locked = locked(babyId, recordId);
        CareRecord changed;
        try {
            changed = locked.complete(OffsetDateTime.ofInstant(clock.instant(), zone), memberId);
        } catch (IllegalStateException exception) {
            throw new CareException(CareErrorCode.RECORD_ALREADY_FINISHED);
        }
        return records.updateTransition(changed).inZone(zone);
    }

    @Override
    @Transactional
    public void cancel(Long memberId, Long babyId, Long recordId) {
        zone(memberId);
        babies.requireActiveMembership(memberId, babyId);
        CareRecord locked = locked(babyId, recordId);
        CareRecord changed;
        try {
            changed = locked.cancel(clock.instant(), memberId);
        } catch (IllegalStateException exception) {
            throw new CareException(CareErrorCode.RECORD_ALREADY_FINISHED);
        }
        records.updateTransition(changed);
    }

    private CareRecord locked(Long babyId, Long recordId) {
        return records.findByBabyAndIdForUpdate(babyId, recordId)
                .orElseThrow(() -> new CareException(CareErrorCode.RECORD_NOT_FOUND));
    }

    private ZoneId zone(Long memberId) {
        return ZoneId.of(members.requireActiveMember(memberId).timezone());
    }

    private boolean activeUniqueViolation(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("uk_care_record_active_baby")) {
                return true;
            }
        }
        return false;
    }
}
