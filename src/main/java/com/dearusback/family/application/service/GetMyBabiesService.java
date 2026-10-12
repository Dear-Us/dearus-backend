package com.dearusback.family.application.service;

import com.dearusback.family.application.model.MyBabySummary;
import com.dearusback.family.application.port.in.GetMyBabiesUseCase;
import com.dearusback.family.application.port.out.BabyQueryPort;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetMyBabiesService implements GetMyBabiesUseCase {

    private final BabyRegistrationMemberAccess members;
    private final BabyQueryPort babies;

    public GetMyBabiesService(BabyRegistrationMemberAccess members, BabyQueryPort babies) {
        this.members = members;
        this.babies = babies;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MyBabySummary> getMyBabies(Long memberId) {
        members.requireActiveMember(memberId);
        return babies.findActiveBabiesByMemberId(memberId);
    }
}
