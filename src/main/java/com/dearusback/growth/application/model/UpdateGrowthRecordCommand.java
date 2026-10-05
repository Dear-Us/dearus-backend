package com.dearusback.growth.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateGrowthRecordCommand(Long memberId, Long babyId, Long growthRecordId, long version,
                                        boolean measuredDatePresent, LocalDate measuredDate,
                                        boolean heightCmPresent, BigDecimal heightCm,
                                        boolean weightKgPresent, BigDecimal weightKg,
                                        boolean memoPresent, String memo) {
}
