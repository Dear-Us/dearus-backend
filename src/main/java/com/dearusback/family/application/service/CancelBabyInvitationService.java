package com.dearusback.family.application.service;

import com.dearusback.family.application.port.in.CancelBabyInvitationUseCase;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.domain.model.BabyInvitation;
import com.dearusback.family.domain.model.BabyInvitationStatus;
import com.dearusback.family.domain.repository.BabyInvitationRepository;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelBabyInvitationService implements CancelBabyInvitationUseCase {

    private final BabyRegistrationMemberAccess members;
    private final BabyOwnerGuard owners;
    private final BabyInvitationRepository invitations;
    private final BabyInvitationExpiry expiry;
    private final Clock clock;

    public CancelBabyInvitationService(BabyRegistrationMemberAccess members, BabyOwnerGuard owners,
                                       BabyInvitationRepository invitations, BabyInvitationExpiry expiry,
                                       Clock clock) {
        this.members = members;
        this.owners = owners;
        this.invitations = invitations;
        this.expiry = expiry;
        this.clock = clock;
    }

    @Override
    // An expired PENDING row must remain EXPIRED even when the request returns 409.
    @Transactional(noRollbackFor = FamilyException.class)
    public void cancel(Long memberId, Long babyId, Long invitationId) {
        members.requireActiveMember(memberId);
        owners.lockAndRequireOwner(memberId, babyId, FamilyErrorCode.BABY_INVITATION_FORBIDDEN);
        BabyInvitation invitation = invitations.findByIdAndBabyIdForUpdate(invitationId, babyId)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.INVITATION_NOT_FOUND));
        Instant now = clock.instant();
        BabyInvitation current = expiry.refresh(invitation, now);
        if (current.status() == BabyInvitationStatus.CANCELED) {
            return;
        }
        if (current.status() != BabyInvitationStatus.PENDING) {
            throw new FamilyException(FamilyErrorCode.INVITATION_CONFLICT);
        }
        invitations.save(current.cancel(now));
    }
}
