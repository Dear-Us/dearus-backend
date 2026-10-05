package com.dearusback.insight.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RoutineStatistics(LocalDate startDate, LocalDate endDate, List<Day> days, Averages averages) {
    public record Day(LocalDate date, long feedingCount, long formulaTotalMl,
                      long directBreastfeedingMinutes, long sleepTotalMinutes, long diaperCount) {
    }

    public record Averages(BigDecimal feedingCountPerDay, BigDecimal formulaTotalMlPerDay,
                           BigDecimal directBreastfeedingMinutesPerDay, BigDecimal sleepMinutesPerDay,
                           BigDecimal diaperCountPerDay) {
    }
}
