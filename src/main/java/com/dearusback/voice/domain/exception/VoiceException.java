package com.dearusback.voice.domain.exception;

import com.dearusback.support.domain.exception.BusinessException;

public class VoiceException extends BusinessException {
    public VoiceException(VoiceErrorCode code) { super(code); }
}
