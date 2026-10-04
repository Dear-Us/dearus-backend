package com.dearusback.identity.application.port.in;

import com.dearusback.identity.application.model.BabyRegistrationMember;

public interface BabyRegistrationMemberAccess {

    BabyRegistrationMember requireActiveMember(Long memberId);

    BabyRegistrationMember lockActiveMember(Long memberId);

    void initializeLastSelectedBabyIdIfAbsent(Long memberId, Long babyId);

    void clearLastSelectedBabyIfMatches(Long babyId);
}
