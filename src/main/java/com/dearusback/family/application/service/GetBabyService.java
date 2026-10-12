package com.dearusback.family.application.service;

import com.dearusback.family.application.model.MyBabySummary;
import com.dearusback.family.application.port.in.GetBabyUseCase;
import com.dearusback.family.application.port.out.BabyQueryPort;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetBabyService implements GetBabyUseCase {

    private final BabyRegistrationMemberAccess members;
    private final BabyQueryPort babies;

    public GetBabyService(BabyRegistrationMemberAccess members, BabyQueryPort babies) {
        this.members = members;
        this.babies = babies;
    }

    @Override
    @Transactional(readOnly = true)
    public MyBabySummary getBaby(Long memberId, Long babyId) {
        members.requireActiveMember(memberId);
        return babies.findAccessibleBaby(memberId, babyId)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.BABY_NOT_FOUND));
    }
}
