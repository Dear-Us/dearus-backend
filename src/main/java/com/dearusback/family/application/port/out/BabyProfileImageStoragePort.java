package com.dearusback.family.application.port.out;

import com.dearusback.family.application.model.BabyImageUpload;

public interface BabyProfileImageStoragePort {
    String store(BabyImageUpload image);
    void deleteOwnedImage(String imageUrl);
}
