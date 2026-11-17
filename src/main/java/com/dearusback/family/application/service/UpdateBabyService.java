package com.dearusback.family.application.service;

import com.dearusback.family.application.model.MyBabySummary;
import com.dearusback.family.application.model.UpdateBabyCommand;
import com.dearusback.family.application.port.in.UpdateBabyUseCase;
import com.dearusback.family.application.port.out.BabyProfileImageStoragePort;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.domain.model.BabyGender;
import com.dearusback.identity.application.model.BabyRegistrationMember;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import com.dearusback.support.domain.model.ExternalImageUrl;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UpdateBabyService implements UpdateBabyUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateBabyService.class);
    private final BabyRegistrationMemberAccess members;
    private final BabyOwnerGuard owners;
    private final BabyProfileImageStoragePort storage;
    private final UpdateBabyTransactionService transaction;
    private final Clock clock;

    public UpdateBabyService(BabyRegistrationMemberAccess members, BabyOwnerGuard owners,
                             BabyProfileImageStoragePort storage, UpdateBabyTransactionService transaction,
                             Clock clock) {
        this.members = members;
        this.owners = owners;
        this.storage = storage;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public MyBabySummary update(UpdateBabyCommand command) {
        if ((command.profileImage() != null && command.removeProfileImage())
                || (command.profileImageUrl() != null
                && (command.profileImage() != null || command.removeProfileImage()))) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_UPDATE);
        }
        BabyRegistrationMember member = members.requireActiveMember(command.memberId());
        owners.requireOwner(command.memberId(), command.babyId(), FamilyErrorCode.BABY_UPDATE_FORBIDDEN);
        if (command.babyName() == null && command.birthDate() == null && command.gender() == null
                && command.profileImage() == null && command.profileImageUrl() == null
                && !command.removeProfileImage()) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_UPDATE);
        }

        String name = command.babyName() == null ? null : BabyInputValidator.normalizeName(command.babyName());
        LocalDate birthDate = command.birthDate() == null ? null
                : BabyInputValidator.parseBirthDate(command.birthDate(), member.timezone(), clock);
        BabyGender gender = command.gender() == null ? null : BabyInputValidator.parseGender(command.gender());
        String externalUrl;
        try {
            externalUrl = ExternalImageUrl.normalize(command.profileImageUrl());
        } catch (IllegalArgumentException exception) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_IMAGE_URL);
        }
        if (externalUrl != null && storage.owns(externalUrl)) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_IMAGE_URL);
        }
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
                    command.removeProfileImage(), uploadedUrl == null ? externalUrl : uploadedUrl);
        } catch (RuntimeException original) {
            if (uploadedUrl != null) {
                deleteQuietly(uploadedUrl);
            }
            throw original;
        }

        if ((uploadedUrl != null || externalUrl != null || command.removeProfileImage())
                && updated.previousImageUrl() != null
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
