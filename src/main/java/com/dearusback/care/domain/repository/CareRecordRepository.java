package com.dearusback.care.domain.repository;

import com.dearusback.care.domain.model.CareRecord;

public interface CareRecordRepository {
    CareRecord save(CareRecord record);
}
