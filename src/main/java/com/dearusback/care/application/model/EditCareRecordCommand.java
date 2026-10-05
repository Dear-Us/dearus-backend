package com.dearusback.care.application.model;

import com.dearusback.care.domain.model.CareRecord.Detail;
import java.time.OffsetDateTime;

public record EditCareRecordCommand(Long babyId, Long recordId, Long memberId, long version,
                                    OffsetDateTime recordedAt, OffsetDateTime startAt, OffsetDateTime endAt,
                                    boolean memoPresent, String memo, Detail detail) {
}
