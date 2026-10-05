package com.dearusback.growth.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateGrowthRecordCommand(Long memberId, Long babyId, LocalDate measuredDate,
                                        BigDecimal heightCm, BigDecimal weightKg, String memo) {
}
