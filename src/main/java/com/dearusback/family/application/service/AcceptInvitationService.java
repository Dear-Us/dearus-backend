package com.dearusback.family.application.service;

import com.dearusback.family.application.model.AcceptedInvitation;
import com.dearusback.family.application.port.in.AcceptInvitationUseCase;
import com.dearusback.family.application.port.out.InvitationTokenHasher;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.domain.model.Baby;
import com.dearusback.family.domain.model.BabyInvitation;
import com.dearusback.family.domain.model.BabyInvitationStatus;
import com.dearusback.family.domain.model.BabyMemberStatus;
import com.dearusback.family.domain.repository.BabyInvitationRepository;
import com.dearusback.family.domain.repository.BabyRepository;
import com.dearusback.identity.application.model.BabyRegistrationMember;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcceptInvitationService implements AcceptInvitationUseCase {

    private final BabyRegistrationMemberAccess members;
    private final BabyInvitationRepository invitations;
    private final BabyRepository babies;
    private final InvitationTokenHasher hasher;
    private final BabyInvitationExpiry expiry;
    private final Clock clock;

    public AcceptInvitationService(BabyRegistrationMemberAccess members, BabyInvitationRepository invitations,
                                   BabyRepository babies, InvitationTokenHasher hasher,
                                   BabyInvitationExpiry expiry, Clock clock) {
        this.members = members;
        this.invitations = invitations;
        this.babies = babies;
        this.hasher = hasher;
        this.expiry = expiry;
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = FamilyException.class)
    public AcceptedInvitation accept(Long memberId, String rawToken) {
        // Match the existing member -> baby -> invitation lock order used by Baby and Invitation writes.
        BabyRegistrationMember member = members.lockActiveMember(memberId);
        String tokenHash = hasher.hash(rawToken);
        Long babyId = invitations.findBabyIdByTokenHash(tokenHash)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.INVITATION_NOT_FOUND));
        Baby baby = babies.findByIdForUpdate(babyId, memberId)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.INVITATION_NOT_FOUND));
        BabyInvitation locked = invitations.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.INVITATION_NOT_FOUND));
        Instant now = clock.instant();
        BabyInvitation current = expiry.refresh(locked, now);
        if (current.status() != BabyInvitationStatus.PENDING) {
            throw new FamilyException(FamilyErrorCode.INVITATION_ACCEPT_CONFLICT);
        }
        if (baby.members().stream().anyMatch(related -> related.memberId().equals(memberId)
                && related.status() == BabyMemberStatus.ACTIVE)) {
            throw new FamilyException(FamilyErrorCode.INVITATION_ACCEPT_CONFLICT);
        }
        babies.saveCaregiverMembership(baby.connectCaregiver(memberId, now), memberId);
        BabyInvitation accepted = invitations.save(current.accept(memberId, now));
        members.initializeLastSelectedBabyIdIfAbsent(memberId, baby.id());
        return new AcceptedInvitation(accepted.id(), baby.id(), baby.name(), accepted.role(),
                accepted.status(), accepted.acceptedAt().atZone(ZoneId.of(member.timezone())).toOffsetDateTime());
    }
}
