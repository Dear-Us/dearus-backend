package com.dearusback.family.application.port.out;

import com.dearusback.family.application.model.BabyImageUpload;

public interface BabyProfileImageStoragePort {
    String store(BabyImageUpload image);
    String readUrl(String storedImageUrl);
    boolean owns(String imageUrl);
    void deleteOwnedImage(String imageUrl);
}
