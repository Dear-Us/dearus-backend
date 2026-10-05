package com.dearusback.care.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CareStatisticsSource(List<Measurement> measurements, List<SleepInterval> sleeps) {
    public record Measurement(Long recordId, Instant recordedAt, Kind kind, Integer amountMl,
                              Integer durationMinutes, BigDecimal temperatureCelsius) {
    }

    public record SleepInterval(Instant startAt, Instant endAt) {
    }

    public enum Kind {
        FORMULA,
        DIRECT_BREASTFEEDING,
        DIAPER_URINE,
        DIAPER_STOOL,
        DIAPER_BOTH,
        TEMPERATURE,
        MEDICINE,
        OTHER
    }
}
