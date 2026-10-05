package com.dearusback.insight.presentation.rest;

import com.dearusback.insight.application.model.DailyStatistics;
import com.dearusback.insight.application.model.GrowthStatistics;
import com.dearusback.insight.application.model.RoutineStatistics;
import com.dearusback.insight.application.port.in.GetStatisticsUseCase;
import com.dearusback.support.application.exception.CommonErrorCode;
import com.dearusback.support.domain.exception.BusinessException;
import com.dearusback.support.presentation.response.ApiResponse;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/babies/{babyId}/stats")
public class StatisticsController {
    private final GetStatisticsUseCase statistics;

    public StatisticsController(GetStatisticsUseCase statistics) {
        this.statistics = statistics;
    }

    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<DailyStatistics>> daily(@AuthenticationPrincipal Long memberId,
                                                               @PathVariable Long babyId,
                                                               @RequestParam(required = false) String date) {
        requirePositive(babyId);
        return ResponseEntity.ok(ApiResponse.success(statistics.daily(memberId, babyId, parse(date))));
    }

    @GetMapping("/routine")
    public ResponseEntity<ApiResponse<RoutineStatistics>> routine(@AuthenticationPrincipal Long memberId,
                                                                   @PathVariable Long babyId,
                                                                   @RequestParam(required = false) String endDate) {
        requirePositive(babyId);
        return ResponseEntity.ok(ApiResponse.success(statistics.routine(memberId, babyId, parse(endDate))));
    }

    @GetMapping("/growth")
    public ResponseEntity<ApiResponse<GrowthStatistics>> growth(@AuthenticationPrincipal Long memberId,
                                                                 @PathVariable Long babyId) {
        requirePositive(babyId);
        return ResponseEntity.ok(ApiResponse.success(statistics.growth(memberId, babyId)));
    }

    private void requirePositive(Long id) {
        if (id == null || id <= 0) {
            throw invalid();
        }
    }

    private LocalDate parse(String raw) {
        if (raw == null || !raw.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw invalid();
        }
        try {
            LocalDate parsed = LocalDate.parse(raw);
            if (parsed.getYear() == 0) {
                throw invalid();
            }
            return parsed;
        } catch (DateTimeParseException exception) {
            throw invalid();
        }
    }

    private BusinessException invalid() {
        return new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }
}
