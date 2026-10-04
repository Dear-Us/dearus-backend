package com.dearusback.family.application.service;

import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.domain.model.Baby;
import com.dearusback.family.domain.model.BabyMember;
import com.dearusback.family.domain.model.BabyMemberRole;
import com.dearusback.family.domain.model.BabyMemberStatus;
import com.dearusback.family.domain.repository.BabyRepository;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteBabyTransactionService {

    private final BabyRegistrationMemberAccess members;
    private final BabyRepository babies;
    private final Clock clock;

    public DeleteBabyTransactionService(BabyRegistrationMemberAccess members, BabyRepository babies, Clock clock) {
        this.members = members;
        this.babies = babies;
        this.clock = clock;
    }

    @Transactional
    public String delete(Long memberId, Long babyId) {
        members.lockActiveMember(memberId);
        Baby baby = babies.findByIdAndAllMembersForUpdate(babyId)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.BABY_NOT_FOUND));
        BabyMember requester = baby.members().stream()
                .filter(related -> related.memberId().equals(memberId))
                .filter(related -> related.status() == BabyMemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.BABY_NOT_FOUND));
        if (requester.role() != BabyMemberRole.OWNER) {
            throw new FamilyException(FamilyErrorCode.BABY_DELETE_FORBIDDEN);
        }
        String previousImageUrl = baby.profileImageUrl();
        babies.markDeleted(baby.markDeleted(clock.instant()));
        members.clearLastSelectedBabyIfMatches(babyId);
        return previousImageUrl;
    }
}
