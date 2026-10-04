package com.dearusback.identity.application.model;

public record IssuedAccessToken(String value, long expiresInSeconds) {
}
