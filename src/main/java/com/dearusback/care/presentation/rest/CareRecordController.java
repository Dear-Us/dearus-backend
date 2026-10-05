package com.dearusback.care.presentation.rest;

import com.dearusback.care.application.port.in.CreateCareRecordUseCase;
import com.dearusback.care.application.port.in.ActiveCareRecordUseCase;
import com.dearusback.care.application.port.in.CareRecordQueryUseCase;
import com.dearusback.care.application.model.CalendarDayCount;
import com.dearusback.care.domain.model.CareRecord.CareRecordType;
import com.dearusback.care.domain.exception.CareErrorCode;
import com.dearusback.care.domain.exception.CareException;
import com.dearusback.care.presentation.dto.CareRecordResponse;
import com.dearusback.care.presentation.dto.CreateCareRecordRequest;
import com.dearusback.care.presentation.dto.StartCareRecordRequest;
import com.dearusback.support.presentation.response.ApiResponse;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/babies/{babyId}/records")
public class CareRecordController {

    private final CreateCareRecordUseCase records;
    private final CareRecordQueryUseCase queries;
    private final ActiveCareRecordUseCase activeRecords;

    public CareRecordController(CreateCareRecordUseCase records, CareRecordQueryUseCase queries,
                                ActiveCareRecordUseCase activeRecords) {
        this.records = records;
        this.queries = queries;
        this.activeRecords = activeRecords;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CareRecordResponse>>> list(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String recordType) {
        requirePositive(babyId);
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw invalidQuery();
        }
        try {
            LocalDate day = LocalDate.parse(date);
            if (day.getYear() == 0) {
                throw invalidQuery();
            }
            CareRecordType type = recordType == null ? null : CareRecordType.valueOf(recordType);
            return ResponseEntity.ok(ApiResponse.success(queries.list(memberId, babyId, day, type)
                    .stream().map(CareRecordResponse::from).toList()));
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            throw invalidQuery();
        }
    }

    @GetMapping("/calendar")
    public ResponseEntity<ApiResponse<List<CalendarDayCount>>> calendar(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId,
            @RequestParam(required = false) String year, @RequestParam(required = false) String month) {
        requirePositive(babyId);
        if (year == null || !year.matches("\\d{4}") || month == null || !month.matches("\\d{1,2}")) {
            throw invalidQuery();
        }
        try {
            int calendarYear = Integer.parseInt(year);
            if (calendarYear == 0) {
                throw invalidQuery();
            }
            YearMonth period = YearMonth.of(calendarYear, Integer.parseInt(month));
            return ResponseEntity.ok(ApiResponse.success(queries.calendar(memberId, babyId, period)));
        } catch (DateTimeException | IllegalArgumentException exception) {
            throw invalidQuery();
        }
    }

    @GetMapping("/{recordId}")
    public ResponseEntity<ApiResponse<CareRecordResponse>> detail(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId, @PathVariable Long recordId) {
        requirePositive(babyId);
        requirePositive(recordId);
        return ResponseEntity.ok(ApiResponse.success(CareRecordResponse.from(
                queries.detail(memberId, babyId, recordId))));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<CareRecordResponse>> active(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId) {
        requirePositive(babyId);
        var record = queries.active(memberId, babyId);
        return ResponseEntity.ok(ApiResponse.success(record == null ? null : CareRecordResponse.from(record)));
    }

    @PostMapping(value = "/start", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CareRecordResponse>> start(@AuthenticationPrincipal Long memberId,
                                                                  @PathVariable Long babyId,
                                                                  @RequestBody JsonNode request) {
        if (babyId == null || babyId <= 0) {
            throw new CareException(CareErrorCode.INVALID_RECORD);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(CareRecordResponse.from(
                activeRecords.start(StartCareRecordRequest.toCommand(babyId, memberId, request)))));
    }

    @PatchMapping("/{recordId}/complete")
    public ResponseEntity<ApiResponse<CareRecordResponse>> complete(@AuthenticationPrincipal Long memberId,
                                                                     @PathVariable Long babyId,
                                                                     @PathVariable Long recordId) {
        requirePositive(babyId);
        requirePositive(recordId);
        return ResponseEntity.ok(ApiResponse.success(CareRecordResponse.from(
                activeRecords.complete(memberId, babyId, recordId))));
    }

    @PatchMapping("/{recordId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(@AuthenticationPrincipal Long memberId,
                                                     @PathVariable Long babyId, @PathVariable Long recordId) {
        requirePositive(babyId);
        requirePositive(recordId);
        activeRecords.cancel(memberId, babyId, recordId);
        return ResponseEntity.ok(ApiResponse.success(null));
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

    private void requirePositive(Long id) {
        if (id == null || id <= 0) {
            throw invalidQuery();
        }
    }

    private CareException invalidQuery() {
        return new CareException(CareErrorCode.INVALID_RECORD_QUERY);
    }
}
