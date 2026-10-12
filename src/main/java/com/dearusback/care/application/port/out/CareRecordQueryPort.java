package com.dearusback.care.application.port.out;

import com.dearusback.care.application.model.CareRecordView;
import com.dearusback.care.domain.model.CareRecord.CareRecordType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Map;

public interface CareRecordQueryPort {
    List<CareRecordView> findCompletedRecords(Long babyId, Instant startInclusive, Instant endExclusive,
                                              CareRecordType type);

    List<Instant> findCompletedRecordedTimes(Long babyId, Instant startInclusive, Instant endExclusive);

    Optional<CareRecordView> findCompletedRecord(Long babyId, Long recordId);

    Optional<CareRecordView> findActiveRecord(Long babyId);

    Map<CareRecordType, Long> countCompletedByType(Long babyId, Instant startInclusive, Instant endExclusive);

    List<CareRecordView> findRecentCompletedRecords(Long babyId, int limit);
}
