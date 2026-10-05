package com.dearusback.family.application.port.out;

import com.dearusback.family.application.model.MyBabySummary;
import com.dearusback.family.application.model.BabyMembershipView;
import com.dearusback.family.application.model.InvitationBabyView;
import java.util.List;
import java.util.Optional;

public interface BabyQueryPort {
    List<MyBabySummary> findActiveBabiesByMemberId(Long memberId);

    Optional<MyBabySummary> findAccessibleBaby(Long memberId, Long babyId);

    List<BabyMembershipView> findActiveMembers(Long babyId);

    Optional<InvitationBabyView> findActiveInvitationBaby(Long babyId);
}
