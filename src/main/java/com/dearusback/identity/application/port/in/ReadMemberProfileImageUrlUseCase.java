package com.dearusback.identity.application.port.in;

public interface ReadMemberProfileImageUrlUseCase {
    String readUrl(Long memberId, String storedImageUrl);
}
