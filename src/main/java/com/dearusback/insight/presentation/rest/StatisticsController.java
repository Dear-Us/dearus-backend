package com.dearusback.insight.presentation.rest;

import com.dearusback.insight.application.model.DailyStatistics;
import com.dearusback.insight.application.model.GrowthStatistics;
import com.dearusback.insight.application.model.RoutineStatistics;
import com.dearusback.insight.application.port.in.GetStatisticsUseCase;
import com.dearusback.support.application.exception.CommonErrorCode;
import com.dearusback.support.domain.exception.BusinessException;
import com.dearusback.support.presentation.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Statistics", description = "회원 timezone 기준 육아·성장 통계")
@SecurityRequirement(name = "bearerAuth")
public class StatisticsController {
    private final GetStatisticsUseCase statistics;

    public StatisticsController(GetStatisticsUseCase statistics) {
        this.statistics = statistics;
    }

    @GetMapping("/daily")
    @Operation(operationId = "getDailyStatistics", summary = "일일 육아 통계", tags = "Statistics", description = "필수 date(YYYY-MM-DD)의 완료·미삭제 Care를 집계한다. 자정을 넘는 SLEEP은 해당 날짜와 겹친 시간만 분할 계산한다. 기록 수와 기간 합계의 날짜 기준은 다를 수 있다. 기록이 없으면 count/total은 0, 계산할 값이 없는 평균·최신값은 null이다.")
    public ResponseEntity<ApiResponse<DailyStatistics>> daily(@AuthenticationPrincipal Long memberId,
                                                               @PathVariable Long babyId,
                                                               @RequestParam(required = false) String date) {
        requirePositive(babyId);
        return ResponseEntity.ok(ApiResponse.success(statistics.daily(memberId, babyId, parse(date))));
    }

    @GetMapping("/routine")
    @Operation(operationId = "getRoutineStatistics", summary = "7일 루틴 통계", tags = "Statistics", description = "필수 endDate를 포함한 7일의 완료·미삭제 Care를 일별 집계한다. SLEEP 기간은 각 날짜와의 overlap으로 분할하며 회원 timezone을 사용한다. 데이터가 없는 합계는 0, 계산할 수 없는 평균은 null일 수 있다.")
    public ResponseEntity<ApiResponse<RoutineStatistics>> routine(@AuthenticationPrincipal Long memberId,
                                                                   @PathVariable Long babyId,
                                                                   @RequestParam(required = false) String endDate) {
        requirePositive(babyId);
        return ResponseEntity.ok(ApiResponse.success(statistics.routine(memberId, babyId, parse(endDate))));
    }

    @GetMapping("/growth")
    @Operation(operationId = "getGrowthStatistics", summary = "성장 통계", tags = "Statistics", description = "삭제되지 않은 성장 기록의 최신 수치·이전 수치 대비 변화·시계열을 조회한다. 키/몸무게는 각각 독립적으로 null일 수 있으며 누락된 수치를 보간하지 않는다.")
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
