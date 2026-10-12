package com.dearusback.identity.application.service;

import com.dearusback.identity.application.model.MyProfileResult;
import com.dearusback.identity.application.model.StoredProfileImage;
import com.dearusback.identity.application.model.UpdateMyProfileCommand;
import com.dearusback.identity.application.port.in.UpdateMyProfileUseCase;
import com.dearusback.identity.application.port.out.ProfileImageStoragePort;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.exception.IdentityException;
import com.dearusback.identity.domain.model.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UpdateMyProfileService implements UpdateMyProfileUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateMyProfileService.class);

    private final UpdateMyProfileTransactionService transaction;
    private final ProfileImageStoragePort storage;

    public UpdateMyProfileService(UpdateMyProfileTransactionService transaction, ProfileImageStoragePort storage) {
        this.transaction = transaction;
        this.storage = storage;
    }

    @Override
    public MyProfileResult update(UpdateMyProfileCommand command) {
        if (command.profileImage() != null && command.removeProfileImage()) {
            throw new IdentityException(IdentityErrorCode.INVALID_PROFILE_UPDATE);
        }
        if (command.memberName() == null && command.profileImage() == null && !command.removeProfileImage()) {
            throw new IdentityException(IdentityErrorCode.INVALID_PROFILE_UPDATE);
        }
        String normalizedName = command.memberName() == null ? null : Member.normalizeName(command.memberName());
        StoredProfileImage uploaded = null;
        if (command.profileImage() != null) {
            try {
                uploaded = storage.store(command.memberId(), command.profileImage());
            } catch (RuntimeException exception) {
                throw new IdentityException(IdentityErrorCode.PROFILE_IMAGE_STORAGE_UNAVAILABLE);
            }
        }

        UpdateMyProfileTransactionService.UpdatedProfile result;
        try {
            result = transaction.update(command.memberId(), normalizedName, command.removeProfileImage(),
                    uploaded == null ? null : uploaded.url());
        } catch (RuntimeException original) {
            if (uploaded != null) {
                deleteQuietly(command.memberId(), uploaded.url());
            }
            throw original;
        }

        if ((uploaded != null || command.removeProfileImage()) && result.previousImageUrl() != null
                && !result.previousImageUrl().equals(result.profile().profileImageUrl())) {
            deleteQuietly(command.memberId(), result.previousImageUrl());
        }
        return result.profile();
    }

    private void deleteQuietly(Long memberId, String imageUrl) {
        try {
            storage.deleteOwnedImage(memberId, imageUrl);
        } catch (RuntimeException exception) {
            log.warn("Profile image cleanup failed for member {}", memberId);
        }
    }
}
