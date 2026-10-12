package com.dearusback.care.application.model;

import com.dearusback.care.domain.model.CareRecord.CareRecordInputType;
import com.dearusback.care.domain.model.CareRecord.CareRecordStatus;
import com.dearusback.care.domain.model.CareRecord.CareRecordType;
import com.dearusback.care.domain.model.CareRecord.Detail;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record CareRecordView(Long recordId, Long babyId, CareRecordType recordType,
                             CareRecordInputType inputType, CareRecordStatus status,
                             OffsetDateTime recordedAt, OffsetDateTime startAt, OffsetDateTime endAt,
                             String memo, Detail detail, long version) {

    public CareRecordView inZone(ZoneId zone) {
        return new CareRecordView(recordId, babyId, recordType, inputType, status,
                recordedAt.atZoneSameInstant(zone).toOffsetDateTime(),
                startAt == null ? null : startAt.atZoneSameInstant(zone).toOffsetDateTime(),
                endAt == null ? null : endAt.atZoneSameInstant(zone).toOffsetDateTime(),
                memo, detail, version);
    }

    public CareRecordView withDetail(Detail loaded) {
        return new CareRecordView(recordId, babyId, recordType, inputType, status,
                recordedAt, startAt, endAt, memo, loaded, version);
    }
}
