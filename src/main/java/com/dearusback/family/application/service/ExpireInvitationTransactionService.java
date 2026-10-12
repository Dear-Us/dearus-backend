package com.dearusback.family.application.service;

import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.domain.model.BabyInvitation;
import com.dearusback.family.domain.repository.BabyInvitationRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpireInvitationTransactionService {

    private final BabyInvitationRepository invitations;
    private final BabyInvitationExpiry expiry;
    private final Clock clock;

    public ExpireInvitationTransactionService(BabyInvitationRepository invitations,
                                              BabyInvitationExpiry expiry, Clock clock) {
        this.invitations = invitations;
        this.expiry = expiry;
        this.clock = clock;
    }

    @Transactional
    public BabyInvitation refresh(Long invitationId) {
        BabyInvitation locked = invitations.findByIdForUpdate(invitationId)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.INVITATION_NOT_FOUND));
        return expiry.refresh(locked, clock.instant());
    }
}
