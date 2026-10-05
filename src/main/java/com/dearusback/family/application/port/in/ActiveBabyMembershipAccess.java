package com.dearusback.family.application.port.in;

public interface ActiveBabyMembershipAccess {
    void requireActiveMembershipForUpdate(Long memberId, Long babyId);
}
