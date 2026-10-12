package com.dearusback.care.application.port.in;

import com.dearusback.care.application.model.EditCareRecordCommand;
import com.dearusback.care.domain.model.CareRecord;

public interface EditCareRecordUseCase {
    CareRecord edit(EditCareRecordCommand command);

    void delete(Long memberId, Long babyId, Long recordId, long version);
}
