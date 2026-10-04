package com.dearusback.identity.application.port.in;

import com.dearusback.identity.application.model.LogoutCommand;

public interface LogoutUseCase {

    void logout(LogoutCommand command);
}
