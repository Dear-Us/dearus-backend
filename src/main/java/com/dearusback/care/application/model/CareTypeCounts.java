package com.dearusback.care.application.model;

import com.dearusback.care.domain.model.CareRecord.CareRecordType;
import java.util.Map;

public record CareTypeCounts(long feeding, long sleep, long diaper, long temperature, long medicine, long other) {
    public static CareTypeCounts from(Map<CareRecordType, Long> counts) {
        return new CareTypeCounts(counts.getOrDefault(CareRecordType.FEEDING, 0L),
                counts.getOrDefault(CareRecordType.SLEEP, 0L),
                counts.getOrDefault(CareRecordType.DIAPER, 0L),
                counts.getOrDefault(CareRecordType.TEMPERATURE, 0L),
                counts.getOrDefault(CareRecordType.MEDICINE, 0L),
                counts.getOrDefault(CareRecordType.OTHER, 0L));
    }

    public long total() {
        return feeding + sleep + diaper + temperature + medicine + other;
    }
}
