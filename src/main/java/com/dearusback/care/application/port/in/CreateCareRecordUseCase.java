package com.dearusback.care.application.port.in;

import com.dearusback.care.application.model.CreateCareRecordCommand;
import com.dearusback.care.domain.model.CareRecord;

public interface CreateCareRecordUseCase {
    CareRecord create(CreateCareRecordCommand command);
}
