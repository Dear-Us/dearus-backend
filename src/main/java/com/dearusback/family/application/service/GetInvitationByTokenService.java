package com.dearusback.family.application.service;

import com.dearusback.family.application.model.InvitationBabyView;
import com.dearusback.family.application.model.InvitationPreview;
import com.dearusback.family.application.port.in.GetInvitationByTokenUseCase;
import com.dearusback.family.application.port.out.BabyQueryPort;
import com.dearusback.family.application.port.out.InvitationTokenHasher;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.domain.model.BabyInvitation;
import com.dearusback.family.domain.repository.BabyInvitationRepository;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

@Service
public class GetInvitationByTokenService implements GetInvitationByTokenUseCase {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Seoul");

    private final BabyInvitationRepository invitations;
    private final InvitationTokenHasher hasher;
    private final BabyQueryPort babies;
    private final ExpireInvitationTransactionService expiry;
    private final Clock clock;

    public GetInvitationByTokenService(BabyInvitationRepository invitations, InvitationTokenHasher hasher,
                                       BabyQueryPort babies, ExpireInvitationTransactionService expiry,
                                       Clock clock) {
        this.invitations = invitations;
        this.hasher = hasher;
        this.babies = babies;
        this.expiry = expiry;
        this.clock = clock;
    }

    @Override
    public InvitationPreview get(String rawToken) {
        BabyInvitation invitation = invitations.findByTokenHash(hasher.hash(rawToken))
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.INVITATION_NOT_FOUND));
        InvitationBabyView baby = babies.findActiveInvitationBaby(invitation.babyId())
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.INVITATION_NOT_FOUND));
        if (invitation.isExpiredAt(clock.instant())) {
            invitation = expiry.refresh(invitation.id());
            baby = babies.findActiveInvitationBaby(invitation.babyId())
                    .orElseThrow(() -> new FamilyException(FamilyErrorCode.INVITATION_NOT_FOUND));
        }
        return new InvitationPreview(baby.babyName(), baby.babyProfileImageUrl(),
                invitation.role(), invitation.status(),
                invitation.expiresAt().atZone(DISPLAY_ZONE).toOffsetDateTime());
    }
}
