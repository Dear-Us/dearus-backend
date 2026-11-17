package com.dearusback.care.presentation.rest;

import com.dearusback.care.application.port.in.CreateCareRecordUseCase;
import com.dearusback.care.application.port.in.ActiveCareRecordUseCase;
import com.dearusback.care.application.port.in.EditCareRecordUseCase;
import com.dearusback.care.application.port.in.CareRecordQueryUseCase;
import com.dearusback.care.application.model.CalendarDayCount;
import com.dearusback.care.domain.model.CareRecord.CareRecordType;
import com.dearusback.care.domain.exception.CareErrorCode;
import com.dearusback.care.domain.exception.CareException;
import com.dearusback.care.application.model.CareRecordResponse;
import com.dearusback.care.presentation.dto.CreateCareRecordRequest;
import com.dearusback.care.presentation.dto.StartCareRecordRequest;
import com.dearusback.care.presentation.dto.EditCareRecordRequest;
import com.dearusback.support.presentation.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/babies/{babyId}/records")
@Tag(name = "Care Record", description = "완료·진행형 육아 기록")
@SecurityRequirement(name = "bearerAuth")
public class CareRecordController {

    private final CreateCareRecordUseCase records;
    private final CareRecordQueryUseCase queries;
    private final ActiveCareRecordUseCase activeRecords;
    private final EditCareRecordUseCase edits;

    public CareRecordController(CreateCareRecordUseCase records, CareRecordQueryUseCase queries,
                                ActiveCareRecordUseCase activeRecords, EditCareRecordUseCase edits) {
        this.records = records;
        this.queries = queries;
        this.activeRecords = activeRecords;
        this.edits = edits;
    }

    @GetMapping
    @Operation(operationId = "listCareRecords", summary = "완료 육아 기록 목록", tags = "Care Record", description = "필수 date(YYYY-MM-DD)와 선택 recordType으로 해당 날짜의 COMPLETED·미삭제 기록을 조회한다. 날짜 경계는 회원의 IANA timezone을 따른다. 공동 보호자 모두 조회 가능.")
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
    @Operation(operationId = "getCareCalendar", summary = "월별 기록 캘린더", tags = "Care Record", description = "필수 year(4자리), month(1~12)로 날짜별 완료 기록 수를 조회한다. 진행 중·취소·삭제 기록은 제외한다.")
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
    @Operation(operationId = "getCareRecord", summary = "완료 기록 상세 조회", tags = "Care Record", description = "같은 Baby의 ACTIVE 보호자가 COMPLETED·미삭제 기록만 조회한다. 접근 불가 또는 상태가 다른 기록은 404.")
    public ResponseEntity<ApiResponse<CareRecordResponse>> detail(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId, @PathVariable Long recordId) {
        requirePositive(babyId);
        requirePositive(recordId);
        return ResponseEntity.ok(ApiResponse.success(CareRecordResponse.from(
                queries.detail(memberId, babyId, recordId))));
    }

    @GetMapping("/active")
    @Operation(operationId = "getActiveCareRecord", summary = "진행 중 기록 조회", tags = "Care Record", description = "Baby의 현재 IN_PROGRESS 기록 한 건을 조회한다. 없으면 공통 성공 봉투의 data가 null이다.")
    public ResponseEntity<ApiResponse<CareRecordResponse>> active(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId) {
        requirePositive(babyId);
        var record = queries.active(memberId, babyId);
        return ResponseEntity.ok(ApiResponse.success(record == null ? null : CareRecordResponse.from(record)));
    }

    @PostMapping(value = "/start", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "startCareRecord", summary = "진행형 기록 시작", tags = "Care Record", description = "ACTIVE 공동 보호자가 SLEEP 또는 DIRECT_BREASTFEEDING만 시작할 수 있다. Baby당 IN_PROGRESS는 최대 한 건이며 중복 시작은 409. 성공 시 201.")
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
    @Operation(operationId = "completeCareRecord", summary = "진행형 기록 완료", tags = "Care Record", description = "ACTIVE 공동 보호자가 IN_PROGRESS 기록을 COMPLETED로 전이한다. 완료 시 완료자 audit가 기록된다. 동시 완료/취소는 한 요청만 성공한다.")
    public ResponseEntity<ApiResponse<CareRecordResponse>> complete(@AuthenticationPrincipal Long memberId,
                                                                     @PathVariable Long babyId,
                                                                     @PathVariable Long recordId) {
        requirePositive(babyId);
        requirePositive(recordId);
        return ResponseEntity.ok(ApiResponse.success(CareRecordResponse.from(
                activeRecords.complete(memberId, babyId, recordId))));
    }

    @PatchMapping("/{recordId}/cancel")
    @Operation(operationId = "cancelCareRecord", summary = "진행형 기록 취소", tags = "Care Record", description = "ACTIVE 공동 보호자가 IN_PROGRESS 기록을 CANCELED로 전이한다. 완료 이력에는 나타나지 않는다. 동시 완료/취소는 한 요청만 성공한다.")
    public ResponseEntity<ApiResponse<Void>> cancel(@AuthenticationPrincipal Long memberId,
                                                     @PathVariable Long babyId, @PathVariable Long recordId) {
        requirePositive(babyId);
        requirePositive(recordId);
        activeRecords.cancel(memberId, babyId, recordId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping(value = "/{recordId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateCareRecord", summary = "완료 기록 수정", tags = "Care Record", description = "COMPLETED 기록만 수정한다. version 필수, 불일치 시 409이므로 최신 상세를 재조회한다. recordType/inputType/status는 변경할 수 없다. 필드 미전달은 유지, memo:null은 제거, detail을 제공하면 유형별 상세 전체를 교체한다. SLEEP의 startAt/endAt 변경은 시간 순서를 검증한다.")
    public ResponseEntity<ApiResponse<CareRecordResponse>> edit(@AuthenticationPrincipal Long memberId,
                                                                 @PathVariable Long babyId,
                                                                 @PathVariable Long recordId,
                                                                 @RequestBody JsonNode request) {
        requirePositive(babyId);
        requirePositive(recordId);
        return ResponseEntity.ok(ApiResponse.success(CareRecordResponse.from(edits.edit(
                EditCareRecordRequest.toCommand(babyId, recordId, memberId, request)))));
    }

    @DeleteMapping("/{recordId}")
    @Operation(operationId = "deleteCareRecord", summary = "완료 기록 삭제", tags = "Care Record", description = "필수 query version으로 COMPLETED 기록을 낙관적 버전 검사 후 soft delete한다. 불일치 시 409; 최신 상세 재조회 후 사용자에게 변경 여부를 확인한다.")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal Long memberId,
                                                     @PathVariable Long babyId, @PathVariable Long recordId,
                                                     @RequestParam(required = false) String version) {
        requirePositive(babyId);
        requirePositive(recordId);
        if (version == null || !version.matches("\\d+")) {
            throw new CareException(CareErrorCode.INVALID_RECORD);
        }
        try {
            edits.delete(memberId, babyId, recordId, Long.parseLong(version));
        } catch (NumberFormatException exception) {
            throw new CareException(CareErrorCode.INVALID_RECORD);
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createCareRecord", summary = "완료 육아 기록 생성", tags = "Care Record", description = "ACTIVE 공동 보호자가 FEEDING/SLEEP/DIAPER/TEMPERATURE/MEDICINE/OTHER 완료 기록을 만든다. SLEEP 요청은 startAt/endAt을 보내고 detail은 생략하며, 응답의 detail은 null이다. 다른 유형은 recordedAt과 유형별 detail을 필수로 보낸다. 성공 시 201이고 생성자 audit가 저장된다.")
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
