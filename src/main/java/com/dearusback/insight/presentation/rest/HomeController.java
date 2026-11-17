package com.dearusback.insight.presentation.rest;

import com.dearusback.insight.application.port.in.GetHomeUseCase;
import com.dearusback.family.application.port.in.ReadBabyProfileImageUrlUseCase;
import com.dearusback.insight.presentation.dto.HomeResponse;
import com.dearusback.support.application.exception.CommonErrorCode;
import com.dearusback.support.domain.exception.BusinessException;
import com.dearusback.support.presentation.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Home", description = "현재 DB 기준 아기 홈 데이터")
@SecurityRequirement(name = "bearerAuth")
public class HomeController {

    private final GetHomeUseCase homes;
    private final ReadBabyProfileImageUrlUseCase imageUrls;

    public HomeController(GetHomeUseCase homes, ReadBabyProfileImageUrlUseCase imageUrls) {
        this.homes = homes;
        this.imageUrls = imageUrls;
    }

    @GetMapping("/api/v1/babies/{babyId}/home")
    @Operation(operationId = "getHome", summary = "홈 데이터 조회", tags = "Home", description = "ACTIVE 공동 보호자가 Baby, activeRecord, todaySummary, lastCompletedRecord, recentRecords를 조회한다. 오늘 경계는 회원 timezone 기준이다. SSE payload 대신 REST 결과가 최신 상태의 권위다.")
    public ResponseEntity<ApiResponse<HomeResponse>> get(@AuthenticationPrincipal Long memberId,
                                                         @PathVariable Long babyId) {
        if (babyId == null || babyId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        return ResponseEntity.ok(ApiResponse.success(
                HomeResponse.from(homes.get(memberId, babyId), imageUrls::readUrl)));
    }
}
