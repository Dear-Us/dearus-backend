package com.dearusback.identity.infrastructure.storage;

import com.dearusback.identity.application.model.ProfileImageUpload;
import com.dearusback.identity.application.model.StoredProfileImage;
import com.dearusback.identity.application.port.out.ProfileImageStoragePort;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.exception.IdentityException;

final class UnavailableProfileImageStorageAdapter implements ProfileImageStoragePort {

    @Override
    public StoredProfileImage store(Long memberId, ProfileImageUpload image) {
        throw new IdentityException(IdentityErrorCode.PROFILE_IMAGE_STORAGE_UNAVAILABLE);
    }

    @Override
    public void deleteOwnedImage(Long memberId, String imageUrl) {
        // No storage client exists when image storage is disabled.
    }
}
