package com.dearusback.family.application.service;

import com.dearusback.family.application.port.in.ActiveBabyMembershipAccess;
import com.dearusback.family.application.port.out.BabyQueryPort;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.domain.model.BabyMemberStatus;
import com.dearusback.family.domain.repository.BabyRepository;
import org.springframework.stereotype.Service;

@Service
public class ActiveBabyMembershipService implements ActiveBabyMembershipAccess {

    private final BabyRepository babies;
    private final BabyQueryPort queries;

    public ActiveBabyMembershipService(BabyRepository babies, BabyQueryPort queries) {
        this.babies = babies;
        this.queries = queries;
    }

    @Override
    public void requireActiveMembership(Long memberId, Long babyId) {
        queries.findAccessibleBaby(memberId, babyId)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.BABY_NOT_FOUND));
    }

    @Override
    public void requireActiveMembershipForUpdate(Long memberId, Long babyId) {
        babies.findByIdForUpdate(babyId, memberId)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.BABY_NOT_FOUND));
        babies.findMemberForUpdate(babyId, memberId)
                .filter(membership -> membership.status() == BabyMemberStatus.ACTIVE)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.BABY_NOT_FOUND));
    }
}
