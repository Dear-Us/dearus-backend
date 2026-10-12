package com.dearusback.identity.application.port.in;

import com.dearusback.identity.application.model.MyProfileResult;

public interface GetMyProfileQuery {

    MyProfileResult getMyProfile(Long memberId);
}
