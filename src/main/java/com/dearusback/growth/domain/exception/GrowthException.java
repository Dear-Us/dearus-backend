package com.dearusback.growth.domain.exception;

import com.dearusback.support.domain.exception.BusinessException;

public class GrowthException extends BusinessException {
    public GrowthException(GrowthErrorCode errorCode) {
        super(errorCode);
    }
}
