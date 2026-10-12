package com.dearusback.identity.application.port.in;

import com.dearusback.identity.application.model.MyProfileResult;
import com.dearusback.identity.application.model.UpdateMyProfileCommand;

public interface UpdateMyProfileUseCase {

    MyProfileResult update(UpdateMyProfileCommand command);
}
