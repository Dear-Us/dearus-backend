package com.dearusback.family.application.port.out;

public interface InvitationTokenHasher {
    String hash(String rawToken);
}
