package com.dearusback.care.application.service;

import com.dearusback.care.application.model.CalendarDayCount;
import com.dearusback.care.application.model.CareRecordView;
import com.dearusback.care.application.port.in.CareRecordQueryUseCase;
import com.dearusback.care.application.port.out.CareRecordQueryPort;
import com.dearusback.care.domain.exception.CareErrorCode;
import com.dearusback.care.domain.exception.CareException;
import com.dearusback.care.domain.model.CareRecord.CareRecordType;
import com.dearusback.family.application.port.in.ActiveBabyMembershipAccess;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CareRecordQueryService implements CareRecordQueryUseCase {

    private final BabyRegistrationMemberAccess members;
    private final ActiveBabyMembershipAccess babies;
    private final CareRecordQueryPort records;

    public CareRecordQueryService(BabyRegistrationMemberAccess members, ActiveBabyMembershipAccess babies,
                                  CareRecordQueryPort records) {
        this.members = members;
        this.babies = babies;
        this.records = records;
    }

    @Override
    public List<CareRecordView> list(Long memberId, Long babyId, LocalDate date, CareRecordType type) {
        ZoneId zone = accessibleZone(memberId, babyId);
        Instant start = date.atStartOfDay(zone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();
        return records.findCompletedRecords(babyId, start, end, type).stream()
                .map(record -> record.inZone(zone)).toList();
    }

    @Override
    public List<CalendarDayCount> calendar(Long memberId, Long babyId, YearMonth month) {
        ZoneId zone = accessibleZone(memberId, babyId);
        Instant start = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        Map<LocalDate, Long> counts = new TreeMap<>();
        for (Instant recordedAt : records.findCompletedRecordedTimes(babyId, start, end)) {
            counts.merge(recordedAt.atZone(zone).toLocalDate(), 1L, Long::sum);
        }
        return counts.entrySet().stream().map(entry -> new CalendarDayCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public CareRecordView detail(Long memberId, Long babyId, Long recordId) {
        ZoneId zone = accessibleZone(memberId, babyId);
        return records.findCompletedRecord(babyId, recordId)
                .map(record -> record.inZone(zone))
                .orElseThrow(() -> new CareException(CareErrorCode.RECORD_NOT_FOUND));
    }

    @Override
    public CareRecordView active(Long memberId, Long babyId) {
        ZoneId zone = accessibleZone(memberId, babyId);
        return records.findActiveRecord(babyId).map(record -> record.inZone(zone)).orElse(null);
    }

    private ZoneId timezone(Long memberId) {
        return ZoneId.of(members.requireActiveMember(memberId).timezone());
    }

    private ZoneId accessibleZone(Long memberId, Long babyId) {
        ZoneId zone = timezone(memberId);
        babies.requireActiveMembership(memberId, babyId);
        return zone;
    }
}
