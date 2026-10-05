package com.dearusback.family.application.service;

import com.dearusback.family.domain.model.BabyInvitation;
import com.dearusback.family.domain.repository.BabyInvitationRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class BabyInvitationExpiry {

    private final BabyInvitationRepository invitations;

    public BabyInvitationExpiry(BabyInvitationRepository invitations) {
        this.invitations = invitations;
    }

    public BabyInvitation refresh(BabyInvitation invitation, Instant now) {
        BabyInvitation current = invitation.expire(now);
        return current == invitation ? invitation : invitations.save(current);
    }
}
