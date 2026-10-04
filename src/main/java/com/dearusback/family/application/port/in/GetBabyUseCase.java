package com.dearusback.family.application.port.in;

import com.dearusback.family.application.model.MyBabySummary;

public interface GetBabyUseCase {
    MyBabySummary getBaby(Long memberId, Long babyId);
}
