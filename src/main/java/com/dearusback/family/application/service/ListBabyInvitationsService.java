package com.dearusback.family.application.service;

import com.dearusback.family.application.model.InvitationSummary;
import com.dearusback.family.application.port.in.ListBabyInvitationsUseCase;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.repository.BabyInvitationRepository;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListBabyInvitationsService implements ListBabyInvitationsUseCase {

    private final BabyRegistrationMemberAccess members;
    private final BabyOwnerGuard owners;
    private final BabyInvitationRepository invitations;
    private final BabyInvitationExpiry expiry;
    private final Clock clock;

    public ListBabyInvitationsService(BabyRegistrationMemberAccess members, BabyOwnerGuard owners,
                                      BabyInvitationRepository invitations, BabyInvitationExpiry expiry,
                                      Clock clock) {
        this.members = members;
        this.owners = owners;
        this.invitations = invitations;
        this.expiry = expiry;
        this.clock = clock;
    }

    @Override
    @Transactional
    public List<InvitationSummary> list(Long memberId, Long babyId) {
        ZoneId zone = ZoneId.of(members.requireActiveMember(memberId).timezone());
        owners.lockAndRequireOwner(memberId, babyId, FamilyErrorCode.BABY_INVITATION_FORBIDDEN);
        Instant now = clock.instant();
        return invitations.findAllByBabyIdForUpdate(babyId).stream()
                .map(invitation -> expiry.refresh(invitation, now))
                .map(invitation -> InvitationSummary.from(invitation, zone)).toList();
    }
}
