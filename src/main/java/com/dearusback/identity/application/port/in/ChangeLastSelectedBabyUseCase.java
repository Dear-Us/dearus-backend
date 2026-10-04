package com.dearusback.identity.application.port.in;

import com.dearusback.identity.application.model.ChangeLastSelectedBabyCommand;

public interface ChangeLastSelectedBabyUseCase {
    Long change(ChangeLastSelectedBabyCommand command);
}
