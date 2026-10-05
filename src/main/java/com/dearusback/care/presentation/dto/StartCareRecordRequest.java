package com.dearusback.care.presentation.dto;

import com.dearusback.care.application.model.StartCareRecordCommand;
import com.dearusback.care.domain.exception.CareErrorCode;
import com.dearusback.care.domain.exception.CareException;
import com.dearusback.care.domain.model.CareRecord.BreastSide;
import com.dearusback.care.domain.model.CareRecord.CareRecordInputType;
import com.dearusback.care.domain.model.CareRecord.CareRecordType;
import com.dearusback.care.domain.model.CareRecord.FeedingType;
import tools.jackson.databind.JsonNode;

public final class StartCareRecordRequest {
    private StartCareRecordRequest() { }

    public static StartCareRecordCommand toCommand(Long babyId, Long memberId, JsonNode body) {
        if (body == null || !body.isObject()) {
            throw invalid();
        }
        try {
            CareRecordType type = CareRecordType.valueOf(text(body, "recordType"));
            String memo = optionalMemo(body);
            int expectedSize = 1 + (body.has("memo") ? 1 : 0);
            BreastSide side = null;
            if (type == CareRecordType.SLEEP) {
                if (body.size() != expectedSize) {
                    throw invalid();
                }
            } else if (type == CareRecordType.FEEDING) {
                JsonNode detail = body.get("detail");
                if (body.size() != expectedSize + 1 || detail == null || !detail.isObject()
                        || detail.size() != 2 || FeedingType.valueOf(text(detail, "feedingType"))
                        != FeedingType.DIRECT_BREASTFEEDING) {
                    throw invalid();
                }
                side = BreastSide.valueOf(text(detail, "breastSide"));
                if (side == BreastSide.BOTH) {
                    throw invalid();
                }
            } else {
                throw invalid();
            }
            return new StartCareRecordCommand(babyId, memberId, type, CareRecordInputType.MANUAL, memo, side);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw invalid();
        }
        return value.asText();
    }

    private static String optionalMemo(JsonNode body) {
        JsonNode value = body.get("memo");
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalid();
        }
        String memo = value.asText().trim();
        return memo.isEmpty() ? null : memo;
    }

    private static CareException invalid() {
        return new CareException(CareErrorCode.INVALID_RECORD);
    }
}
