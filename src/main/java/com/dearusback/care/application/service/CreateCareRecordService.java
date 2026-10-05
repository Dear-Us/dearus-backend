package com.dearusback.care.application.service;

import com.dearusback.care.application.model.CreateCareRecordCommand;
import com.dearusback.care.application.port.in.CreateCareRecordUseCase;
import com.dearusback.care.domain.exception.CareErrorCode;
import com.dearusback.care.domain.exception.CareException;
import com.dearusback.care.domain.model.CareRecord;
import com.dearusback.care.domain.repository.CareRecordRepository;
import com.dearusback.family.application.port.in.ActiveBabyMembershipAccess;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateCareRecordService implements CreateCareRecordUseCase {

    private final BabyRegistrationMemberAccess members;
    private final ActiveBabyMembershipAccess babies;
    private final CareRecordRepository records;
    private final Clock clock;

    public CreateCareRecordService(BabyRegistrationMemberAccess members, ActiveBabyMembershipAccess babies,
                                   CareRecordRepository records, Clock clock) {
        this.members = members;
        this.babies = babies;
        this.records = records;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CareRecord create(CreateCareRecordCommand command) {
        members.requireActiveMember(command.memberId());
        babies.requireActiveMembershipForUpdate(command.memberId(), command.babyId());
        CareRecord record;
        try {
            record = CareRecord.completed(command.babyId(), command.memberId(),
                    command.recordType(), command.inputType(), command.recordedAt(), command.startAt(),
                    command.endAt(), command.memo(), command.detail(), clock.instant());
        } catch (IllegalArgumentException exception) {
            throw new CareException(CareErrorCode.INVALID_RECORD);
        }
        return records.save(record);
    }
}
