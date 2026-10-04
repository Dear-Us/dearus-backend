package com.dearusback.identity.domain.type;

import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.exception.IdentityException;

public enum SocialProvider {
    GOOGLE,
    KAKAO,
    NAVER;

    public static SocialProvider from(String value) {
        if (value == null) {
            throw new IdentityException(IdentityErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IdentityException(IdentityErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);
        }
    }
}
