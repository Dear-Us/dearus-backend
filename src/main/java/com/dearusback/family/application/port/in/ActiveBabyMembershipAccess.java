package com.dearusback.family.application.port.in;

public interface ActiveBabyMembershipAccess {
    void requireActiveMembership(Long memberId, Long babyId);

    void requireActiveMembershipForUpdate(Long memberId, Long babyId);
}
