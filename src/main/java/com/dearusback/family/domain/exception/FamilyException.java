package com.dearusback.family.domain.exception;

import com.dearusback.support.domain.exception.BusinessException;

public class FamilyException extends BusinessException {
    public FamilyException(FamilyErrorCode errorCode) {
        super(errorCode);
    }
}
