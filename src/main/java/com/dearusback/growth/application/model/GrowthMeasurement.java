package com.dearusback.growth.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GrowthMeasurement(Long growthRecordId, LocalDate measuredDate,
                                BigDecimal heightCm, BigDecimal weightKg) {
}
