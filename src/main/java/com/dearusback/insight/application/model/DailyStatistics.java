package com.dearusback.insight.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyStatistics(LocalDate date, Feeding feeding, Sleep sleep, Diaper diaper,
                              Temperature temperature, Count medicine, Count other) {
    public record Feeding(long totalCount, long formulaCount, long formulaTotalMl, BigDecimal formulaAverageMl,
                          long directBreastfeedingCount, long directBreastfeedingTotalMinutes,
                          BigDecimal directBreastfeedingAverageMinutes) {
    }

    public record Sleep(long count, long totalMinutes, BigDecimal averageMinutes) {
    }

    public record Diaper(long totalCount, long urineCount, long stoolCount) {
    }

    public record Temperature(long count, BigDecimal latestTemperatureCelsius,
                              BigDecimal minTemperatureCelsius, BigDecimal maxTemperatureCelsius) {
    }

    public record Count(long count) {
    }
}
