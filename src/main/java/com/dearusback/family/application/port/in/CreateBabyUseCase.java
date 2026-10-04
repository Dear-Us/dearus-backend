package com.dearusback.family.application.port.in;

import com.dearusback.family.application.model.CreateBabyCommand;
import com.dearusback.family.application.model.CreateBabyResult;

public interface CreateBabyUseCase {
    CreateBabyResult create(CreateBabyCommand command);
}
