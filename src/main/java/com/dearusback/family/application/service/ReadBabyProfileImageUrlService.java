package com.dearusback.family.application.service;

import com.dearusback.family.application.port.in.ReadBabyProfileImageUrlUseCase;
import com.dearusback.family.application.port.out.BabyProfileImageStoragePort;
import org.springframework.stereotype.Service;

@Service
public class ReadBabyProfileImageUrlService implements ReadBabyProfileImageUrlUseCase {

    private final BabyProfileImageStoragePort storage;

    public ReadBabyProfileImageUrlService(BabyProfileImageStoragePort storage) {
        this.storage = storage;
    }

    @Override
    public String readUrl(String storedImageUrl) {
        return storage.readUrl(storedImageUrl);
    }
}
