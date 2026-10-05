package com.dearusback.care.application.model;

import com.dearusback.care.domain.model.CareRecord.BreastSide;
import com.dearusback.care.domain.model.CareRecord.CareRecordInputType;
import com.dearusback.care.domain.model.CareRecord.CareRecordType;

public record StartCareRecordCommand(Long babyId, Long memberId, CareRecordType recordType,
                                     CareRecordInputType inputType, String memo, BreastSide breastSide) {
}
