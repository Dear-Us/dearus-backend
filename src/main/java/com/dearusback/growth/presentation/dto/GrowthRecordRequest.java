package com.dearusback.growth.presentation.dto;

import com.dearusback.growth.application.model.CreateGrowthRecordCommand;
import com.dearusback.growth.application.model.UpdateGrowthRecordCommand;
import com.dearusback.growth.domain.exception.GrowthErrorCode;
import com.dearusback.growth.domain.exception.GrowthException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import tools.jackson.databind.JsonNode;

public final class GrowthRecordRequest {
    private GrowthRecordRequest() {
    }

    public static CreateGrowthRecordCommand create(Long memberId, Long babyId, JsonNode body) {
        requireObject(body);
        requireOnly(body, "measuredDate", "heightCm", "weightKg", "memo");
        return new CreateGrowthRecordCommand(memberId, babyId, date(body.get("measuredDate")),
                decimal(body.get("heightCm")), decimal(body.get("weightKg")), memo(body.get("memo")));
    }

    public static UpdateGrowthRecordCommand update(Long memberId, Long babyId, Long growthRecordId,
                                                   JsonNode body) {
        requireObject(body);
        requireOnly(body, "version", "measuredDate", "heightCm", "weightKg", "memo");
        if (body.size() < 2) {
            throw invalid();
        }
        return new UpdateGrowthRecordCommand(memberId, babyId, growthRecordId, version(body.get("version")),
                body.has("measuredDate"), body.has("measuredDate") ? date(body.get("measuredDate")) : null,
                body.has("heightCm"), decimal(body.get("heightCm")),
                body.has("weightKg"), decimal(body.get("weightKg")),
                body.has("memo"), memo(body.get("memo")));
    }

    public static long version(JsonNode value) {
        if (value == null || !value.isIntegralNumber()) {
            throw invalid();
        }
        BigDecimal number = value.decimalValue();
        if (number.signum() < 0 || number.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            throw invalid();
        }
        return number.longValueExact();
    }

    private static LocalDate date(JsonNode value) {
        if (value == null || !value.isTextual() || !value.asText().matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw invalid();
        }
        try {
            LocalDate parsed = LocalDate.parse(value.asText());
            if (parsed.getYear() == 0) {
                throw invalid();
            }
            return parsed;
        } catch (DateTimeParseException exception) {
            throw invalid();
        }
    }

    private static BigDecimal decimal(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            throw invalid();
        }
        return value.decimalValue();
    }

    private static String memo(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalid();
        }
        return value.asText();
    }

    private static void requireObject(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw invalid();
        }
    }

    private static void requireOnly(JsonNode body, String... allowed) {
        int count = 0;
        for (String field : allowed) {
            if (body.has(field)) {
                count++;
            }
        }
        if (count != body.size()) {
            throw invalid();
        }
    }

    private static GrowthException invalid() {
        return new GrowthException(GrowthErrorCode.INVALID_GROWTH_RECORD);
    }
}
