package com.dearusback.identity.application.port.out;

import com.dearusback.identity.application.model.ProfileImageUpload;
import com.dearusback.identity.application.model.StoredProfileImage;

public interface ProfileImageStoragePort {

    StoredProfileImage store(Long memberId, ProfileImageUpload image);

    boolean owns(Long memberId, String imageUrl);

    void deleteOwnedImage(Long memberId, String imageUrl);
}
