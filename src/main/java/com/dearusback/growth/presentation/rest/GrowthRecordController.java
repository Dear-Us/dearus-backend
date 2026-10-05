package com.dearusback.growth.presentation.rest;

import com.dearusback.growth.application.model.GrowthRecordResponse;
import com.dearusback.growth.application.port.in.GrowthRecordUseCase;
import com.dearusback.growth.domain.exception.GrowthErrorCode;
import com.dearusback.growth.domain.exception.GrowthException;
import com.dearusback.growth.presentation.dto.GrowthRecordRequest;
import com.dearusback.support.presentation.response.ApiResponse;
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
public class GrowthRecordController {
    private final GrowthRecordUseCase records;

    public GrowthRecordController(GrowthRecordUseCase records) {
        this.records = records;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<GrowthRecordResponse>>> list(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId) {
        requirePositive(babyId);
        return ResponseEntity.ok(ApiResponse.success(records.list(memberId, babyId)
                .stream().map(GrowthRecordResponse::from).toList()));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<GrowthRecordResponse>> create(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId, @RequestBody JsonNode request) {
        requirePositive(babyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(GrowthRecordResponse.from(
                records.create(GrowthRecordRequest.create(memberId, babyId, request)))));
    }

    @GetMapping("/{growthRecordId}")
    public ResponseEntity<ApiResponse<GrowthRecordResponse>> detail(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId,
            @PathVariable Long growthRecordId) {
        requirePositive(babyId);
        requirePositive(growthRecordId);
        return ResponseEntity.ok(ApiResponse.success(GrowthRecordResponse.from(
                records.detail(memberId, babyId, growthRecordId))));
    }

    @PatchMapping(value = "/{growthRecordId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<GrowthRecordResponse>> update(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId,
            @PathVariable Long growthRecordId, @RequestBody JsonNode request) {
        requirePositive(babyId);
        requirePositive(growthRecordId);
        return ResponseEntity.ok(ApiResponse.success(GrowthRecordResponse.from(
                records.update(GrowthRecordRequest.update(memberId, babyId, growthRecordId, request)))));
    }

    @DeleteMapping("/{growthRecordId}")
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
