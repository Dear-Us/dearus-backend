package com.dearusback.identity.application.port.in;

import com.dearusback.identity.application.model.SocialLoginCommand;
import com.dearusback.identity.application.model.SocialLoginResult;

public interface SocialLoginUseCase {

    SocialLoginResult login(SocialLoginCommand command);
}
