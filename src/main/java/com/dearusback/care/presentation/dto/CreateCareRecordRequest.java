package com.dearusback.care.presentation.dto;

import com.dearusback.care.application.model.CreateCareRecordCommand;
import com.dearusback.care.domain.exception.CareErrorCode;
import com.dearusback.care.domain.exception.CareException;
import com.dearusback.care.domain.model.CareRecord.BreastSide;
import com.dearusback.care.domain.model.CareRecord.CareRecordInputType;
import com.dearusback.care.domain.model.CareRecord.CareRecordType;
import com.dearusback.care.domain.model.CareRecord.DiaperDetail;
import com.dearusback.care.domain.model.CareRecord.DiaperType;
import com.dearusback.care.domain.model.CareRecord.FeedingDetail;
import com.dearusback.care.domain.model.CareRecord.FeedingType;
import com.dearusback.care.domain.model.CareRecord.MedicineDetail;
import com.dearusback.care.domain.model.CareRecord.MedicineDoseUnit;
import com.dearusback.care.domain.model.CareRecord.OtherDetail;
import com.dearusback.care.domain.model.CareRecord.TemperatureDetail;
import com.dearusback.care.domain.model.CareRecord.TemperatureMeasuredPart;
import com.dearusback.care.domain.model.CareRecord.Detail;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import tools.jackson.databind.JsonNode;

public final class CreateCareRecordRequest {

    private CreateCareRecordRequest() {
    }

    public static CreateCareRecordCommand toCommand(Long babyId, Long memberId, JsonNode body) {
        if (body == null || !body.isObject()) {
            throw invalid();
        }
        try {
            CareRecordType type = requiredEnum(body, "recordType", CareRecordType.class);
            boolean sleep = type == CareRecordType.SLEEP;
            if (body.size() != 3 + (body.has("memo") ? 1 : 0)
                    || (sleep && (body.has("recordedAt") || body.has("detail")))
                    || (!sleep && (body.has("startAt") || body.has("endAt")))) {
                throw invalid();
            }
            OffsetDateTime recordedAt = sleep ? null : requiredTime(body, "recordedAt");
            OffsetDateTime startAt = sleep ? requiredTime(body, "startAt") : null;
            OffsetDateTime endAt = sleep ? requiredTime(body, "endAt") : null;
            String memo = optionalText(body, "memo");
            Detail detail = sleep ? null : detail(type, body.get("detail"));
            return new CreateCareRecordCommand(babyId, memberId, type, CareRecordInputType.MANUAL,
                    recordedAt, startAt, endAt, memo, detail);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw invalid();
        }
    }

    static Detail detail(CareRecordType type, JsonNode node) {
        if (node == null || !node.isObject()) {
            throw invalid();
        }
        return switch (type) {
            case FEEDING -> feeding(node);
            case DIAPER -> {
                requireFields(node, "diaperType", "condition", "color");
                yield new DiaperDetail(requiredEnum(node, "diaperType", DiaperType.class),
                        optionalText(node, "condition"), optionalText(node, "color"));
            }
            case TEMPERATURE -> {
                exactFields(node, "temperatureCelsius", "measuredPart");
                yield new TemperatureDetail(requiredDecimal(node, "temperatureCelsius"),
                        requiredEnum(node, "measuredPart", TemperatureMeasuredPart.class));
            }
            case MEDICINE -> {
                exactFields(node, "medicineName", "doseValue", "doseUnit");
                yield new MedicineDetail(requiredText(node, "medicineName"), requiredDecimal(node, "doseValue"),
                        requiredEnum(node, "doseUnit", MedicineDoseUnit.class));
            }
            case OTHER -> {
                exactFields(node, "title");
                yield new OtherDetail(requiredText(node, "title"));
            }
            case SLEEP -> throw invalid();
        };
    }

    private static FeedingDetail feeding(JsonNode node) {
        FeedingType type = requiredEnum(node, "feedingType", FeedingType.class);
        if (type == FeedingType.FORMULA) {
            exactFields(node, "feedingType", "amountMl");
            return new FeedingDetail(type, requiredPositiveInt(node, "amountMl"), null, null);
        }
        exactFields(node, "feedingType", "breastSide", "durationMinutes");
        return new FeedingDetail(type, null, requiredEnum(node, "breastSide", BreastSide.class),
                requiredPositiveInt(node, "durationMinutes"));
    }

    private static void exactFields(JsonNode node, String... fields) {
        requireFields(node, fields);
        if (node.size() != fields.length) {
            throw invalid();
        }
    }

    private static void requireFields(JsonNode node, String... allowed) {
        int count = 0;
        for (String field : allowed) {
            if (node.has(field)) {
                count++;
            }
        }
        if (count != node.size() || !node.has(allowed[0])) {
            throw invalid();
        }
    }

    private static OffsetDateTime requiredTime(JsonNode node, String field) {
        return OffsetDateTime.parse(requiredText(node, field));
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw invalid();
        }
        return value.asText();
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalid();
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static int requiredPositiveInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber()) {
            throw invalid();
        }
        BigDecimal number = value.decimalValue();
        if (number.signum() <= 0 || number.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            throw invalid();
        }
        return number.intValueExact();
    }

    private static BigDecimal requiredDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw invalid();
        }
        return value.decimalValue();
    }

    private static <T extends Enum<T>> T requiredEnum(JsonNode node, String field, Class<T> enumType) {
        return Enum.valueOf(enumType, requiredText(node, field));
    }

    private static CareException invalid() {
        return new CareException(CareErrorCode.INVALID_RECORD);
    }
}
