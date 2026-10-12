package com.dearusback.family.application.service;

import com.dearusback.family.application.model.MyBabySummary;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.model.Baby;
import com.dearusback.family.domain.model.BabyGender;
import com.dearusback.family.domain.model.BabyMemberRole;
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
    private final BabyOwnerGuard owners;
    private final Clock clock;

    public UpdateBabyTransactionService(BabyRegistrationMemberAccess members, BabyRepository babies,
                                        BabyOwnerGuard owners, Clock clock) {
        this.members = members;
        this.babies = babies;
        this.owners = owners;
        this.clock = clock;
    }

    @Transactional
    public UpdatedBaby update(Long memberId, Long babyId, String name, LocalDate birthDate,
                              BabyGender gender, boolean removeImage, String uploadedImageUrl) {
        BabyRegistrationMember member = members.lockActiveMember(memberId);
        Baby baby = owners.lockAndRequireOwner(memberId, babyId, FamilyErrorCode.BABY_UPDATE_FORBIDDEN);
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
