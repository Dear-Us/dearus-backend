package com.dearusback.insight.application.port.in;

import com.dearusback.insight.application.model.DailyStatistics;
import com.dearusback.insight.application.model.GrowthStatistics;
import com.dearusback.insight.application.model.RoutineStatistics;
import java.time.LocalDate;

public interface GetStatisticsUseCase {
    DailyStatistics daily(Long memberId, Long babyId, LocalDate date);

    RoutineStatistics routine(Long memberId, Long babyId, LocalDate endDate);

    GrowthStatistics growth(Long memberId, Long babyId);
}
