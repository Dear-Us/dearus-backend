package com.dearusback.family.infrastructure.persistence;

import com.dearusback.family.domain.model.BabyMemberRole;
import com.dearusback.family.domain.model.BabyMemberStatus;
import com.dearusback.identity.application.port.out.MemberWithdrawalFamilyPort;
import java.time.Instant;
import org.springframework.stereotype.Repository;

@Repository
public class FamilyMemberWithdrawalAdapter implements MemberWithdrawalFamilyPort {

    private final SpringDataBabyMemberRepository memberships;

    public FamilyMemberWithdrawalAdapter(SpringDataBabyMemberRepository memberships) {
        this.memberships = memberships;
    }

    @Override
    public boolean leaveCaregiverMembershipsIfNoActiveOwner(Long memberId, Instant endedAt) {
        var active = memberships.findActiveMembershipsByMemberForUpdate(memberId, BabyMemberStatus.ACTIVE);
        if (active.stream().anyMatch(membership -> membership.role() == BabyMemberRole.OWNER)) {
            return false;
        }
        active.stream()
                .filter(membership -> membership.role() == BabyMemberRole.CAREGIVER)
                .forEach(membership -> membership.leave(endedAt));
        memberships.flush();
        return true;
    }
}
