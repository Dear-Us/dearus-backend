package com.dearusback.family.application.service;

import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.model.BabyInvitation;
import com.dearusback.family.domain.repository.BabyInvitationRepository;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateBabyInvitationTransactionService {

    private final BabyRegistrationMemberAccess members;
    private final BabyOwnerGuard owners;
    private final BabyInvitationRepository invitations;
    private final Clock clock;

    public CreateBabyInvitationTransactionService(BabyRegistrationMemberAccess members, BabyOwnerGuard owners,
                                                   BabyInvitationRepository invitations, Clock clock) {
        this.members = members;
        this.owners = owners;
        this.invitations = invitations;
        this.clock = clock;
    }

    @Transactional
    public BabyInvitation create(Long memberId, Long babyId, String tokenHash) {
        members.lockActiveMember(memberId);
        owners.lockAndRequireOwner(memberId, babyId, FamilyErrorCode.BABY_INVITATION_FORBIDDEN);
        return invitations.save(BabyInvitation.create(babyId, memberId, tokenHash, clock.instant()));
    }
}
