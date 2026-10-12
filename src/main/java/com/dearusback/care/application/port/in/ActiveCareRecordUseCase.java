package com.dearusback.care.application.port.in;

import com.dearusback.care.application.model.StartCareRecordCommand;
import com.dearusback.care.domain.model.CareRecord;

public interface ActiveCareRecordUseCase {
    CareRecord start(StartCareRecordCommand command);

    CareRecord complete(Long memberId, Long babyId, Long recordId);

    void cancel(Long memberId, Long babyId, Long recordId);
}
