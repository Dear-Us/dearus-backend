package com.dearusback.support.domain.exception;

/**
 * Transport-neutral categories used to map errors at the presentation boundary.
 */
public enum ErrorType {
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    INTERNAL_SERVER_ERROR
}
