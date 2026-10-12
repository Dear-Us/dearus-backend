package com.dearusback.identity.application.port.out;

public interface RefreshTokenHasher {

    String hash(String rawToken);
}
