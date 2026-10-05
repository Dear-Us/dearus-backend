package com.dearusback.insight.application.port.in;

import com.dearusback.insight.application.model.HomeResult;

public interface GetHomeUseCase {
    HomeResult get(Long memberId, Long babyId);
}
