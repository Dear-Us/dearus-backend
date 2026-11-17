package com.dearusback.identity.application.port.in;

import com.dearusback.identity.application.model.SocialLoginResult;

public interface GuestLoginUseCase {

    SocialLoginResult loginGuest();
}
