package com.dearusback.insight.application.service;

import com.dearusback.care.application.model.CareStatisticsSource;
import com.dearusback.care.application.model.CareStatisticsSource.Measurement;
import com.dearusback.care.application.model.CareStatisticsSource.SleepInterval;
import com.dearusback.insight.application.model.DailyStatistics;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

final class CareStatisticsCalculator {
    private CareStatisticsCalculator() {
    }

    static DailyStatistics daily(LocalDate date, ZoneId zone, CareStatisticsSource source) {
        Instant start = date.atStartOfDay(zone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();
        long feedingCount = 0;
        long formulaCount = 0;
        long formulaMl = 0;
        long directCount = 0;
        long directMinutes = 0;
        long diaperCount = 0;
        long urineCount = 0;
        long stoolCount = 0;
        long temperatureCount = 0;
        Measurement latestTemperature = null;
        BigDecimal minTemperature = null;
        BigDecimal maxTemperature = null;
        long medicineCount = 0;
        long otherCount = 0;

        for (Measurement measurement : source.measurements()) {
            if (measurement.recordedAt().isBefore(start) || !measurement.recordedAt().isBefore(end)) {
                continue;
            }
            switch (measurement.kind()) {
                case FORMULA -> {
                    feedingCount++;
                    formulaCount++;
                    formulaMl += measurement.amountMl();
                }
                case DIRECT_BREASTFEEDING -> {
                    feedingCount++;
                    directCount++;
                    directMinutes += measurement.durationMinutes();
                }
                case DIAPER_URINE -> {
                    diaperCount++;
                    urineCount++;
                }
                case DIAPER_STOOL -> {
                    diaperCount++;
                    stoolCount++;
                }
                case DIAPER_BOTH -> {
                    diaperCount++;
                    urineCount++;
                    stoolCount++;
                }
                case TEMPERATURE -> {
                    temperatureCount++;
                    BigDecimal value = measurement.temperatureCelsius();
                    if (minTemperature == null || value.compareTo(minTemperature) < 0) {
                        minTemperature = value;
                    }
                    if (maxTemperature == null || value.compareTo(maxTemperature) > 0) {
                        maxTemperature = value;
                    }
                    if (latestTemperature == null || measurement.recordedAt().isAfter(latestTemperature.recordedAt())
                            || (measurement.recordedAt().equals(latestTemperature.recordedAt())
                            && measurement.recordId() > latestTemperature.recordId())) {
                        latestTemperature = measurement;
                    }
                }
                case MEDICINE -> medicineCount++;
                case OTHER -> otherCount++;
            }
        }

        long sleepCount = 0;
        Duration sleepDuration = Duration.ZERO;
        for (SleepInterval interval : source.sleeps()) {
            Instant overlapStart = interval.startAt().isAfter(start) ? interval.startAt() : start;
            Instant overlapEnd = interval.endAt().isBefore(end) ? interval.endAt() : end;
            if (overlapStart.isBefore(overlapEnd)) {
                sleepCount++;
                sleepDuration = sleepDuration.plus(Duration.between(overlapStart, overlapEnd));
            }
        }
        long sleepMinutes = sleepDuration.toMinutes();
        return new DailyStatistics(date,
                new DailyStatistics.Feeding(feedingCount, formulaCount, formulaMl, average(formulaMl, formulaCount),
                        directCount, directMinutes, average(directMinutes, directCount)),
                new DailyStatistics.Sleep(sleepCount, sleepMinutes, average(sleepMinutes, sleepCount)),
                new DailyStatistics.Diaper(diaperCount, urineCount, stoolCount),
                new DailyStatistics.Temperature(temperatureCount,
                        latestTemperature == null ? null : latestTemperature.temperatureCelsius(),
                        minTemperature, maxTemperature),
                new DailyStatistics.Count(medicineCount), new DailyStatistics.Count(otherCount));
    }

    static BigDecimal average(long total, long count) {
        return count == 0 ? null : BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }
}
