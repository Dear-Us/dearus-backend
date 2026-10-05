package com.dearusback.family.application.service;

import com.dearusback.family.application.port.in.ActiveBabyMembershipAccess;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.domain.model.BabyMemberStatus;
import com.dearusback.family.domain.repository.BabyRepository;
import org.springframework.stereotype.Service;

@Service
public class ActiveBabyMembershipService implements ActiveBabyMembershipAccess {

    private final BabyRepository babies;

    public ActiveBabyMembershipService(BabyRepository babies) {
        this.babies = babies;
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
