package com.dearusback.care.presentation.rest;

import com.dearusback.care.application.port.in.CreateCareRecordUseCase;
import com.dearusback.care.domain.exception.CareErrorCode;
import com.dearusback.care.domain.exception.CareException;
import com.dearusback.care.presentation.dto.CareRecordResponse;
import com.dearusback.care.presentation.dto.CreateCareRecordRequest;
import com.dearusback.support.presentation.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/babies/{babyId}/records")
public class CareRecordController {

    private final CreateCareRecordUseCase records;

    public CareRecordController(CreateCareRecordUseCase records) {
        this.records = records;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CareRecordResponse>> create(@AuthenticationPrincipal Long memberId,
                                                                    @PathVariable Long babyId,
                                                                    @RequestBody JsonNode request) {
        if (babyId == null || babyId <= 0) {
            throw new CareException(CareErrorCode.INVALID_RECORD);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(CareRecordResponse.from(
                records.create(CreateCareRecordRequest.toCommand(babyId, memberId, request)))));
    }
}
