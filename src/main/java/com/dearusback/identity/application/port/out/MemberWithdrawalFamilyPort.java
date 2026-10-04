package com.dearusback.identity.application.port.out;

import java.time.Instant;

public interface MemberWithdrawalFamilyPort {
    boolean leaveCaregiverMembershipsIfNoActiveOwner(Long memberId, Instant endedAt);
}
