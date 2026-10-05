package com.dearusback.family.application.service;

import com.dearusback.family.application.port.in.LeaveBabyCaregiverUseCase;
import com.dearusback.family.application.port.in.RemoveBabyCaregiverUseCase;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.domain.model.BabyMember;
import com.dearusback.family.domain.model.BabyMemberRole;
import com.dearusback.family.domain.model.BabyMemberStatus;
import com.dearusback.family.domain.repository.BabyRepository;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndBabyMembershipService implements RemoveBabyCaregiverUseCase, LeaveBabyCaregiverUseCase {

    private final BabyRegistrationMemberAccess members;
    private final BabyOwnerGuard owners;
    private final BabyRepository babies;
    private final Clock clock;

    public EndBabyMembershipService(BabyRegistrationMemberAccess members, BabyOwnerGuard owners,
                                    BabyRepository babies, Clock clock) {
        this.members = members;
        this.owners = owners;
        this.babies = babies;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void remove(Long requesterId, Long babyId, Long targetMemberId) {
        members.requireActiveMember(requesterId);
        owners.lockAndRequireOwner(requesterId, babyId, FamilyErrorCode.BABY_MEMBER_MANAGEMENT_FORBIDDEN);
        BabyMember target = babies.findMemberForUpdate(babyId, targetMemberId)
                .filter(membership -> membership.status() == BabyMemberStatus.ACTIVE)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.BABY_MEMBER_NOT_FOUND));
        if (target.role() == BabyMemberRole.OWNER) {
            throw new FamilyException(FamilyErrorCode.BABY_MEMBER_OWNER_CONFLICT);
        }
        if (babies.updateMembershipStatus(babyId, target.removeCaregiver(clock.instant())) != 1) {
            throw new FamilyException(FamilyErrorCode.BABY_MEMBER_NOT_FOUND);
        }
        members.clearLastSelectedBabyIfMatches(targetMemberId, babyId);
    }

    @Override
    @Transactional
    public void leave(Long memberId, Long babyId) {
        members.requireActiveMember(memberId);
        babies.findByIdForUpdate(babyId, memberId)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.BABY_NOT_FOUND));
        BabyMember own = babies.findMemberForUpdate(babyId, memberId)
                .filter(membership -> membership.status() == BabyMemberStatus.ACTIVE)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.BABY_NOT_FOUND));
        if (own.role() == BabyMemberRole.OWNER) {
            throw new FamilyException(FamilyErrorCode.BABY_MEMBER_OWNER_CONFLICT);
        }
        if (babies.updateMembershipStatus(babyId, own.leaveCaregiver(clock.instant())) != 1) {
            throw new FamilyException(FamilyErrorCode.BABY_NOT_FOUND);
        }
        members.clearLastSelectedBabyIfMatches(memberId, babyId);
    }
}
