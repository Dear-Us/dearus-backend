package com.dearusback.care.domain.repository;

import com.dearusback.care.domain.model.CareRecord;
import java.util.Optional;

public interface CareRecordRepository {
    CareRecord save(CareRecord record);

    Optional<CareRecord> findByBabyAndIdForUpdate(Long babyId, Long recordId);

    CareRecord updateTransition(CareRecord record);

    Optional<CareRecord> findByBabyAndId(Long babyId, Long recordId);

    CareRecord updateCompleted(CareRecord record, boolean replaceDetail);
}
