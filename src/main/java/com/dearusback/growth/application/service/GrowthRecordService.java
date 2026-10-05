package com.dearusback.growth.application.service;

import com.dearusback.family.application.port.in.ActiveBabyMembershipAccess;
import com.dearusback.growth.application.model.CreateGrowthRecordCommand;
import com.dearusback.growth.application.model.UpdateGrowthRecordCommand;
import com.dearusback.growth.application.port.in.GrowthRecordUseCase;
import com.dearusback.growth.domain.exception.GrowthErrorCode;
import com.dearusback.growth.domain.exception.GrowthException;
import com.dearusback.growth.domain.model.GrowthRecord;
import com.dearusback.growth.domain.repository.GrowthRecordRepository;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GrowthRecordService implements GrowthRecordUseCase {
    private final BabyRegistrationMemberAccess members;
    private final ActiveBabyMembershipAccess babies;
    private final GrowthRecordRepository records;
    private final Clock clock;

    public GrowthRecordService(BabyRegistrationMemberAccess members, ActiveBabyMembershipAccess babies,
                               GrowthRecordRepository records, Clock clock) {
        this.members = members;
        this.babies = babies;
        this.records = records;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GrowthRecord> list(Long memberId, Long babyId) {
        authorize(memberId, babyId);
        return records.findAllByBaby(babyId);
    }

    @Override
    @Transactional
    public GrowthRecord create(CreateGrowthRecordCommand command) {
        ZoneId zone = authorize(command.memberId(), command.babyId());
        requireNotFuture(command.measuredDate(), zone);
        try {
            GrowthRecord record = GrowthRecord.create(command.babyId(), command.memberId(),
                    command.measuredDate(), command.heightCm(), command.weightKg(), command.memo(), clock.instant());
            return records.save(record);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalid();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public GrowthRecord detail(Long memberId, Long babyId, Long growthRecordId) {
        authorize(memberId, babyId);
        return find(babyId, growthRecordId);
    }

    @Override
    @Transactional
    public GrowthRecord update(UpdateGrowthRecordCommand command) {
        ZoneId zone = authorize(command.memberId(), command.babyId());
        GrowthRecord existing = find(command.babyId(), command.growthRecordId());
        requireVersion(existing, command.version());
        LocalDate date = command.measuredDatePresent() ? command.measuredDate() : existing.measuredDate();
        requireNotFuture(date, zone);
        try {
            GrowthRecord changed = existing.edit(date,
                    command.heightCmPresent() ? command.heightCm() : existing.heightCm(),
                    command.weightKgPresent() ? command.weightKg() : existing.weightKg(),
                    command.memoPresent() ? command.memo() : existing.memo(), command.memberId(), clock.instant());
            return records.update(changed);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalid();
        } catch (OptimisticLockingFailureException exception) {
            throw conflict();
        }
    }

    @Override
    @Transactional
    public void delete(Long memberId, Long babyId, Long growthRecordId, long version) {
        authorize(memberId, babyId);
        GrowthRecord existing = find(babyId, growthRecordId);
        requireVersion(existing, version);
        try {
            records.update(existing.softDelete(memberId, clock.instant()));
        } catch (OptimisticLockingFailureException exception) {
            throw conflict();
        }
    }

    private ZoneId authorize(Long memberId, Long babyId) {
        ZoneId zone = ZoneId.of(members.requireActiveMember(memberId).timezone());
        babies.requireActiveMembership(memberId, babyId);
        return zone;
    }

    private GrowthRecord find(Long babyId, Long growthRecordId) {
        return records.findByBabyAndId(babyId, growthRecordId)
                .orElseThrow(() -> new GrowthException(GrowthErrorCode.GROWTH_RECORD_NOT_FOUND));
    }

    private void requireNotFuture(LocalDate measuredDate, ZoneId zone) {
        if (measuredDate == null || measuredDate.isAfter(LocalDate.ofInstant(clock.instant(), zone))) {
            throw invalid();
        }
    }

    private void requireVersion(GrowthRecord record, long version) {
        if (record.version() != version) {
            throw conflict();
        }
    }

    private GrowthException invalid() {
        return new GrowthException(GrowthErrorCode.INVALID_GROWTH_RECORD);
    }

    private GrowthException conflict() {
        return new GrowthException(GrowthErrorCode.GROWTH_RECORD_VERSION_CONFLICT);
    }
}
