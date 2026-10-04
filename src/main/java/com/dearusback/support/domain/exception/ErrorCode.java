package com.dearusback.support.domain.exception;

public interface ErrorCode {

    String code();

    String message();

    ErrorType type();
}
