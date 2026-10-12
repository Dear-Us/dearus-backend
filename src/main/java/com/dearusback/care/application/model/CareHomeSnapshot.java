package com.dearusback.care.application.model;

import java.util.List;

public record CareHomeSnapshot(CareRecordView activeRecord, CareTypeCounts todayCounts,
                               List<CareRecordView> recentRecords) {
    public CareHomeSnapshot {
        recentRecords = List.copyOf(recentRecords);
    }
}
