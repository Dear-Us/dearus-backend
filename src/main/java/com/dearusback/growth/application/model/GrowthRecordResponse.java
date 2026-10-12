package com.dearusback.growth.application.model;

import com.dearusback.growth.domain.model.GrowthRecord;
import java.math.BigDecimal;
import java.time.LocalDate;

public record GrowthRecordResponse(Long growthRecordId, Long babyId, LocalDate measuredDate,
                                   BigDecimal heightCm, BigDecimal weightKg, String memo, long version) {
    public static GrowthRecordResponse from(GrowthRecord record) {
        return new GrowthRecordResponse(record.id(), record.babyId(), record.measuredDate(),
                record.heightCm(), record.weightKg(), record.memo(), record.version());
    }
}
