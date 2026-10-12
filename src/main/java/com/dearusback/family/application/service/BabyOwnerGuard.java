package com.dearusback.family.application.service;

import com.dearusback.family.application.model.MyBabySummary;
import com.dearusback.family.application.port.out.BabyQueryPort;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.domain.model.Baby;
import com.dearusback.family.domain.model.BabyMember;
import com.dearusback.family.domain.model.BabyMemberRole;
import com.dearusback.family.domain.model.BabyMemberStatus;
import com.dearusback.family.domain.repository.BabyRepository;
import org.springframework.stereotype.Component;

@Component
public class BabyOwnerGuard {

    private final BabyQueryPort queries;
    private final BabyRepository babies;

    public BabyOwnerGuard(BabyQueryPort queries, BabyRepository babies) {
        this.queries = queries;
        this.babies = babies;
    }

    public void requireOwner(Long memberId, Long babyId, FamilyErrorCode forbidden) {
        MyBabySummary accessible = queries.findAccessibleBaby(memberId, babyId)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.BABY_NOT_FOUND));
        if (accessible.myRole() != BabyMemberRole.OWNER) {
            throw new FamilyException(forbidden);
        }
    }

    public Baby lockAndRequireOwner(Long memberId, Long babyId, FamilyErrorCode forbidden) {
        return requireLockedOwner(memberId, babyId, forbidden, false);
    }

    public Baby lockAndRequireOwnerWithAllMembers(Long memberId, Long babyId, FamilyErrorCode forbidden) {
        return requireLockedOwner(memberId, babyId, forbidden, true);
    }

    private Baby requireLockedOwner(Long memberId, Long babyId, FamilyErrorCode forbidden, boolean allMembers) {
        Baby baby = (allMembers ? babies.findByIdAndAllMembersForUpdate(babyId)
                : babies.findByIdForUpdate(babyId, memberId))
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.BABY_NOT_FOUND));
        BabyMember membership = babies.findMemberForUpdate(babyId, memberId)
                .filter(related -> related.status() == BabyMemberStatus.ACTIVE)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.BABY_NOT_FOUND));
        if (membership.role() != BabyMemberRole.OWNER) {
            throw new FamilyException(forbidden);
        }
        return baby;
    }
}
