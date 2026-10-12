package com.dearusback.identity.domain.exception;

import com.dearusback.support.domain.exception.BusinessException;

public class IdentityException extends BusinessException {

    public IdentityException(IdentityErrorCode errorCode) {
        super(errorCode);
    }
}
