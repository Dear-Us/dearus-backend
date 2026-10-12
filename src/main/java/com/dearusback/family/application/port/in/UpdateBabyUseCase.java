package com.dearusback.family.application.port.in;

import com.dearusback.family.application.model.MyBabySummary;
import com.dearusback.family.application.model.UpdateBabyCommand;

public interface UpdateBabyUseCase {
    MyBabySummary update(UpdateBabyCommand command);
}
