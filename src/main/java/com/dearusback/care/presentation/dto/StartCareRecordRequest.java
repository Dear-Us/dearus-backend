package com.dearusback.care.presentation.dto;

import com.dearusback.care.application.model.ActiveCareRecordCommandParser;
import com.dearusback.care.application.model.StartCareRecordCommand;
import tools.jackson.databind.JsonNode;

public final class StartCareRecordRequest {
    private StartCareRecordRequest() { }

    public static StartCareRecordCommand toCommand(Long babyId, Long memberId, JsonNode body) {
        return ActiveCareRecordCommandParser.toCommand(babyId, memberId, body);
    }
}
