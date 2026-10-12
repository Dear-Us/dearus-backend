package com.dearusback.identity.application.port.out;

public interface AccessibleBabyPort {
    boolean existsActiveMembership(Long memberId, Long babyId);
}
