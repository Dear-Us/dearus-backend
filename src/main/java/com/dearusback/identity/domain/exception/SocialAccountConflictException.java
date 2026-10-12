package com.dearusback.identity.domain.exception;

public final class SocialAccountConflictException extends IdentityException {

    public SocialAccountConflictException() {
        super(IdentityErrorCode.SOCIAL_ACCOUNT_CONFLICT);
    }
}
