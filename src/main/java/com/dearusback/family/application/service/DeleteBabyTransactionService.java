package com.dearusback.family.application.service;

import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.model.Baby;
import com.dearusback.family.domain.repository.BabyRepository;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteBabyTransactionService {

    private final BabyRegistrationMemberAccess members;
    private final BabyRepository babies;
    private final BabyOwnerGuard owners;
    private final Clock clock;

    public DeleteBabyTransactionService(BabyRegistrationMemberAccess members, BabyRepository babies,
                                        BabyOwnerGuard owners, Clock clock) {
        this.members = members;
        this.babies = babies;
        this.owners = owners;
        this.clock = clock;
    }

    @Transactional
    public String delete(Long memberId, Long babyId) {
        members.lockActiveMember(memberId);
        Baby baby = owners.lockAndRequireOwnerWithAllMembers(memberId, babyId,
                FamilyErrorCode.BABY_DELETE_FORBIDDEN);
        String previousImageUrl = baby.profileImageUrl();
        babies.markDeleted(baby.markDeleted(clock.instant()));
        members.clearLastSelectedBabyIfMatches(babyId);
        return previousImageUrl;
    }
}
