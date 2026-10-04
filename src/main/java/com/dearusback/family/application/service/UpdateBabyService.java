package com.dearusback.family.application.service;

import com.dearusback.family.application.model.MyBabySummary;
import com.dearusback.family.application.model.UpdateBabyCommand;
import com.dearusback.family.application.port.in.UpdateBabyUseCase;
import com.dearusback.family.application.port.out.BabyProfileImageStoragePort;
import com.dearusback.family.application.port.out.BabyQueryPort;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.domain.model.BabyGender;
import com.dearusback.family.domain.model.BabyMemberRole;
import com.dearusback.identity.application.model.BabyRegistrationMember;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UpdateBabyService implements UpdateBabyUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateBabyService.class);
    private final BabyRegistrationMemberAccess members;
    private final BabyQueryPort babies;
    private final BabyProfileImageStoragePort storage;
    private final UpdateBabyTransactionService transaction;
    private final Clock clock;

    public UpdateBabyService(BabyRegistrationMemberAccess members, BabyQueryPort babies,
                             BabyProfileImageStoragePort storage, UpdateBabyTransactionService transaction,
                             Clock clock) {
        this.members = members;
        this.babies = babies;
        this.storage = storage;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public MyBabySummary update(UpdateBabyCommand command) {
        if (command.profileImage() != null && command.removeProfileImage()) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_UPDATE);
        }
        BabyRegistrationMember member = members.requireActiveMember(command.memberId());
        MyBabySummary accessible = babies.findAccessibleBaby(command.memberId(), command.babyId())
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.BABY_NOT_FOUND));
        if (accessible.myRole() != BabyMemberRole.OWNER) {
            throw new FamilyException(FamilyErrorCode.BABY_UPDATE_FORBIDDEN);
        }
        if (command.babyName() == null && command.birthDate() == null && command.gender() == null
                && command.profileImage() == null && !command.removeProfileImage()) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_UPDATE);
        }

        String name = command.babyName() == null ? null : BabyInputValidator.normalizeName(command.babyName());
        LocalDate birthDate = command.birthDate() == null ? null
                : BabyInputValidator.parseBirthDate(command.birthDate(), member.timezone(), clock);
        BabyGender gender = command.gender() == null ? null : BabyInputValidator.parseGender(command.gender());
        String uploadedUrl = null;
        if (command.profileImage() != null) {
            try {
                uploadedUrl = storage.store(command.profileImage());
            } catch (RuntimeException exception) {
                throw new FamilyException(FamilyErrorCode.BABY_IMAGE_STORAGE_UNAVAILABLE);
            }
        }

        UpdateBabyTransactionService.UpdatedBaby updated;
        try {
            updated = transaction.update(command.memberId(), command.babyId(), name, birthDate, gender,
                    command.removeProfileImage(), uploadedUrl);
        } catch (RuntimeException original) {
            if (uploadedUrl != null) {
                deleteQuietly(uploadedUrl);
            }
            throw original;
        }

        if ((uploadedUrl != null || command.removeProfileImage()) && updated.previousImageUrl() != null
                && !updated.previousImageUrl().equals(updated.baby().profileImageUrl())) {
            deleteQuietly(updated.previousImageUrl());
        }
        return updated.baby();
    }

    private void deleteQuietly(String imageUrl) {
        try {
            storage.deleteOwnedImage(imageUrl);
        } catch (RuntimeException exception) {
            log.warn("Baby profile image cleanup failed", exception);
        }
    }
}
