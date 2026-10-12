package com.dearusback.care.application.model;

import com.dearusback.care.domain.model.CareRecord.CareRecordInputType;
import com.dearusback.care.domain.model.CareRecord.CareRecordType;
import com.dearusback.care.domain.model.CareRecord.Detail;
import java.time.OffsetDateTime;

public record CreateCareRecordCommand(Long babyId, Long memberId, CareRecordType recordType,
                                      CareRecordInputType inputType, OffsetDateTime recordedAt,
                                      OffsetDateTime startAt, OffsetDateTime endAt, String memo, Detail detail) {
}
