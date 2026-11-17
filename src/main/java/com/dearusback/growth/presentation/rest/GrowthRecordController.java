package com.dearusback.growth.presentation.rest;

import com.dearusback.growth.application.model.GrowthRecordResponse;
import com.dearusback.growth.application.port.in.GrowthRecordUseCase;
import com.dearusback.growth.domain.exception.GrowthErrorCode;
import com.dearusback.growth.domain.exception.GrowthException;
import com.dearusback.growth.presentation.dto.GrowthRecordRequest;
import com.dearusback.support.presentation.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/babies/{babyId}/growth-records")
@Tag(name = "Growth", description = "공동 보호자 성장 기록")
@SecurityRequirement(name = "bearerAuth")
public class GrowthRecordController {
    private final GrowthRecordUseCase records;

    public GrowthRecordController(GrowthRecordUseCase records) {
        this.records = records;
    }

    @GetMapping
    @Operation(operationId = "listGrowthRecords", summary = "성장 기록 목록", tags = "Growth", description = "ACTIVE 공동 보호자가 삭제되지 않은 성장 기록을 날짜 최신순으로 조회한다. 같은 날짜의 여러 기록을 허용한다.")
    public ResponseEntity<ApiResponse<List<GrowthRecordResponse>>> list(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId) {
        requirePositive(babyId);
        return ResponseEntity.ok(ApiResponse.success(records.list(memberId, babyId)
                .stream().map(GrowthRecordResponse::from).toList()));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createGrowthRecord", summary = "성장 기록 생성", tags = "Growth", description = "measuredDate(YYYY-MM-DD), heightCm/weightKg 중 적어도 하나가 필요하다. 미래 날짜는 회원 timezone 기준으로 거절한다. 같은 날짜 여러 기록이 가능하며 성공 시 201과 version=0을 반환한다.")
    public ResponseEntity<ApiResponse<GrowthRecordResponse>> create(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId, @RequestBody JsonNode request) {
        requirePositive(babyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(GrowthRecordResponse.from(
                records.create(GrowthRecordRequest.create(memberId, babyId, request)))));
    }

    @GetMapping("/{growthRecordId}")
    @Operation(operationId = "getGrowthRecord", summary = "성장 기록 상세 조회", tags = "Growth", description = "해당 Baby의 ACTIVE 공동 보호자만 조회한다. 삭제 또는 접근 불가 기록은 404다. 수정·삭제 전 version을 확보한다.")
    public ResponseEntity<ApiResponse<GrowthRecordResponse>> detail(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId,
            @PathVariable Long growthRecordId) {
        requirePositive(babyId);
        requirePositive(growthRecordId);
        return ResponseEntity.ok(ApiResponse.success(GrowthRecordResponse.from(
                records.detail(memberId, babyId, growthRecordId))));
    }

    @PatchMapping(value = "/{growthRecordId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateGrowthRecord", summary = "성장 기록 수정", tags = "Growth", description = "body의 version 필수. 필드 미전달은 유지, 명시적 null은 해당 nullable 수치를 지운다. 최종 heightCm/weightKg 중 적어도 하나는 필수. stale version은 409이며 최신 상세를 재조회한다.")
    public ResponseEntity<ApiResponse<GrowthRecordResponse>> update(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId,
            @PathVariable Long growthRecordId, @RequestBody JsonNode request) {
        requirePositive(babyId);
        requirePositive(growthRecordId);
        return ResponseEntity.ok(ApiResponse.success(GrowthRecordResponse.from(
                records.update(GrowthRecordRequest.update(memberId, babyId, growthRecordId, request)))));
    }

    @DeleteMapping("/{growthRecordId}")
    @Operation(operationId = "deleteGrowthRecord", summary = "성장 기록 삭제", tags = "Growth", description = "필수 query version으로 낙관적 충돌을 검사한 뒤 soft delete한다. 공동 보호자도 삭제할 수 있으며 stale version은 409다.")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId,
            @PathVariable Long growthRecordId, @RequestParam(required = false) String version) {
        requirePositive(babyId);
        requirePositive(growthRecordId);
        if (version == null || !version.matches("\\d+")) {
            throw invalid();
        }
        try {
            records.delete(memberId, babyId, growthRecordId, Long.parseLong(version));
        } catch (NumberFormatException exception) {
            throw invalid();
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private void requirePositive(Long id) {
        if (id == null || id <= 0) {
            throw invalid();
        }
    }

    private GrowthException invalid() {
        return new GrowthException(GrowthErrorCode.INVALID_GROWTH_RECORD);
    }
}
