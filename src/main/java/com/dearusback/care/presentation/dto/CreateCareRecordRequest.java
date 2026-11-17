package com.dearusback.care.presentation.dto;

import com.dearusback.care.application.model.CareRecordCommandParser;
import com.dearusback.care.application.model.CreateCareRecordCommand;
import com.dearusback.care.domain.model.CareRecord.CareRecordType;
import com.dearusback.care.domain.model.CareRecord.Detail;
import tools.jackson.databind.JsonNode;

public final class CreateCareRecordRequest {
    private CreateCareRecordRequest() { }

    public static CreateCareRecordCommand toCommand(Long babyId, Long memberId, JsonNode body) {
        return CareRecordCommandParser.toCommand(babyId, memberId, body);
    }

    static Detail detail(CareRecordType type, JsonNode body) {
        return CareRecordCommandParser.detail(type, body);
    }
}
