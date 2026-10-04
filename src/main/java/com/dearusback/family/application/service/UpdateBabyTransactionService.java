package com.dearusback.family.application.service;

import com.dearusback.family.application.model.MyBabySummary;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.domain.model.Baby;
import com.dearusback.family.domain.model.BabyGender;
import com.dearusback.family.domain.model.BabyMember;
import com.dearusback.family.domain.model.BabyMemberRole;
import com.dearusback.family.domain.model.BabyMemberStatus;
import com.dearusback.family.domain.repository.BabyRepository;
import com.dearusback.identity.application.model.BabyRegistrationMember;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateBabyTransactionService {

    private final BabyRegistrationMemberAccess members;
    private final BabyRepository babies;
    private final Clock clock;

    public UpdateBabyTransactionService(BabyRegistrationMemberAccess members, BabyRepository babies, Clock clock) {
        this.members = members;
        this.babies = babies;
        this.clock = clock;
    }

    @Transactional
    public UpdatedBaby update(Long memberId, Long babyId, String name, LocalDate birthDate,
                              BabyGender gender, boolean removeImage, String uploadedImageUrl) {
        BabyRegistrationMember member = members.lockActiveMember(memberId);
        Baby baby = babies.findByIdForUpdate(babyId, memberId)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.BABY_NOT_FOUND));
        BabyMember membership = baby.members().stream()
                .filter(related -> related.memberId().equals(memberId))
                .filter(related -> related.status() == BabyMemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.BABY_NOT_FOUND));
        if (membership.role() != BabyMemberRole.OWNER) {
            throw new FamilyException(FamilyErrorCode.BABY_UPDATE_FORBIDDEN);
        }
        if (birthDate != null) {
            BabyInputValidator.requireNotFuture(birthDate, member.timezone(), clock);
        }
        String nextImageUrl = removeImage ? null
                : uploadedImageUrl != null ? uploadedImageUrl : baby.profileImageUrl();
        Baby changed = baby.changeInformation(
                name == null ? baby.name() : name,
                birthDate == null ? baby.birthDate() : birthDate,
                gender == null ? baby.gender() : gender,
                nextImageUrl, clock.instant());
        Baby saved = babies.update(changed);
        return new UpdatedBaby(new MyBabySummary(saved.id(), saved.name(), saved.birthDate(), saved.gender(),
                saved.profileImageUrl(), BabyMemberRole.OWNER), baby.profileImageUrl());
    }

    public record UpdatedBaby(MyBabySummary baby, String previousImageUrl) {
    }
}
