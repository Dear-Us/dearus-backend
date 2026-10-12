package com.dearusback.care.domain.exception;

import com.dearusback.support.domain.exception.BusinessException;

public class CareException extends BusinessException {
    public CareException(CareErrorCode errorCode) {
        super(errorCode);
    }
}
