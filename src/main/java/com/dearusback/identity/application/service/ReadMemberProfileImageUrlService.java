package com.dearusback.identity.application.service;

import com.dearusback.identity.application.port.in.ReadMemberProfileImageUrlUseCase;
import com.dearusback.identity.application.port.out.ProfileImageStoragePort;
import org.springframework.stereotype.Service;

@Service
public class ReadMemberProfileImageUrlService implements ReadMemberProfileImageUrlUseCase {

    private final ProfileImageStoragePort storage;

    public ReadMemberProfileImageUrlService(ProfileImageStoragePort storage) {
        this.storage = storage;
    }

    @Override
    public String readUrl(Long memberId, String storedImageUrl) {
        return storage.readUrl(memberId, storedImageUrl);
    }
}
