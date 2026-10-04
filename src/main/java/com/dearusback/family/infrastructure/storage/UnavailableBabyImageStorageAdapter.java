package com.dearusback.family.infrastructure.storage;

import com.dearusback.family.application.model.BabyImageUpload;
import com.dearusback.family.application.port.out.BabyProfileImageStoragePort;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;

public final class UnavailableBabyImageStorageAdapter implements BabyProfileImageStoragePort {
    @Override
    public String store(BabyImageUpload image) {
        throw new FamilyException(FamilyErrorCode.BABY_IMAGE_STORAGE_UNAVAILABLE);
    }

    @Override
    public void deleteOwnedImage(String imageUrl) {
        // No object could have been uploaded while storage was unavailable.
    }
}
