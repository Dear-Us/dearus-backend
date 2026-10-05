package com.dearusback.family.application.service;

import com.dearusback.family.application.model.CreatedInvitation;
import com.dearusback.family.application.port.in.CreateBabyInvitationUseCase;
import com.dearusback.family.application.port.out.InvitationTokenGenerator;
import com.dearusback.family.application.port.out.InvitationTokenHasher;
import com.dearusback.family.application.port.out.InvitationUrlPort;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.model.BabyInvitation;
import com.dearusback.identity.application.model.BabyRegistrationMember;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

@Service
public class CreateBabyInvitationService implements CreateBabyInvitationUseCase {

    private final BabyRegistrationMemberAccess members;
    private final BabyOwnerGuard owners;
    private final InvitationTokenGenerator generator;
    private final InvitationTokenHasher hasher;
    private final InvitationUrlPort urls;
    private final CreateBabyInvitationTransactionService transaction;

    public CreateBabyInvitationService(BabyRegistrationMemberAccess members, BabyOwnerGuard owners,
                                       InvitationTokenGenerator generator, InvitationTokenHasher hasher,
                                       InvitationUrlPort urls, CreateBabyInvitationTransactionService transaction) {
        this.members = members;
        this.owners = owners;
        this.generator = generator;
        this.hasher = hasher;
        this.urls = urls;
        this.transaction = transaction;
    }

    @Override
    public CreatedInvitation create(Long memberId, Long babyId) {
        BabyRegistrationMember member = members.requireActiveMember(memberId);
        owners.requireOwner(memberId, babyId, FamilyErrorCode.BABY_INVITATION_FORBIDDEN);
        String rawToken = generator.generate();
        String invitationUrl = urls.createUrl(rawToken);
        BabyInvitation saved = transaction.create(memberId, babyId, hasher.hash(rawToken));
        return new CreatedInvitation(saved.id(), invitationUrl, saved.role(), saved.status(),
                saved.expiresAt().atZone(ZoneId.of(member.timezone())).toOffsetDateTime());
    }
}
