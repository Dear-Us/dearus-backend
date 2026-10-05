package com.dearusback.insight.application.model;

import com.dearusback.growth.application.model.GrowthMeasurement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record GrowthStatistics(Latest latest, ChangeFromPrevious changeFromPrevious,
                               List<GrowthMeasurement> points) {
    public record Latest(LocalDate measuredDate, BigDecimal heightCm, BigDecimal weightKg) {
    }

    public record ChangeFromPrevious(BigDecimal heightCm, BigDecimal weightKg) {
    }
}
