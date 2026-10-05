package com.dearusback.care.presentation.dto;

import com.dearusback.care.application.model.EditCareRecordCommand;
import com.dearusback.care.domain.exception.CareErrorCode;
import com.dearusback.care.domain.exception.CareException;
import com.dearusback.care.domain.model.CareRecord.CareRecordType;
import com.dearusback.care.domain.model.CareRecord.Detail;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import tools.jackson.databind.JsonNode;

public final class EditCareRecordRequest {
    private EditCareRecordRequest() { }

    public static EditCareRecordCommand toCommand(Long babyId, Long recordId, Long memberId, JsonNode body) {
        if (body == null || !body.isObject()) {
            throw invalid();
        }
        try {
            long version = requiredVersion(body.get("version"));
            int edits = count(body, "recordedAt") + count(body, "startAt") + count(body, "endAt")
                    + count(body, "memo") + count(body, "detail");
            if (edits == 0 || body.size() != edits + 1) {
                throw invalid();
            }
            Detail detail = body.has("detail") ? parseDetail(body.get("detail")) : null;
            return new EditCareRecordCommand(babyId, recordId, memberId, version,
                    time(body, "recordedAt"), time(body, "startAt"), time(body, "endAt"),
                    body.has("memo"), memo(body), detail);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw invalid();
        }
    }

    public static long requiredVersion(JsonNode value) {
        if (value == null || !value.isIntegralNumber()) {
            throw invalid();
        }
        BigDecimal number = value.decimalValue();
        if (number.signum() < 0 || number.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            throw invalid();
        }
        return number.longValueExact();
    }

    private static int count(JsonNode body, String field) {
        return body.has(field) ? 1 : 0;
    }

    private static OffsetDateTime time(JsonNode body, String field) {
        if (!body.has(field)) {
            return null;
        }
        JsonNode value = body.get(field);
        if (!value.isTextual()) {
            throw invalid();
        }
        return OffsetDateTime.parse(value.asText());
    }

    private static String memo(JsonNode body) {
        JsonNode value = body.get("memo");
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalid();
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static Detail parseDetail(JsonNode detail) {
        if (detail == null || !detail.isObject()) {
            throw invalid();
        }
        CareRecordType type;
        if (detail.has("feedingType") || detail.has("amountMl") || detail.has("breastSide")
                || detail.has("durationMinutes")) {
            type = CareRecordType.FEEDING;
        } else if (detail.has("diaperType") || detail.has("condition") || detail.has("color")) {
            type = CareRecordType.DIAPER;
        } else if (detail.has("temperatureCelsius") || detail.has("measuredPart")) {
            type = CareRecordType.TEMPERATURE;
        } else if (detail.has("medicineName") || detail.has("doseValue") || detail.has("doseUnit")) {
            type = CareRecordType.MEDICINE;
        } else if (detail.has("title")) {
            type = CareRecordType.OTHER;
        } else {
            throw invalid();
        }
        return CreateCareRecordRequest.detail(type, detail);
    }

    private static CareException invalid() {
        return new CareException(CareErrorCode.INVALID_RECORD);
    }
}
