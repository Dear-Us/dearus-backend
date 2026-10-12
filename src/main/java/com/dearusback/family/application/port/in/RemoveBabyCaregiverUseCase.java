package com.dearusback.family.application.port.in;

public interface RemoveBabyCaregiverUseCase {
    void remove(Long requesterId, Long babyId, Long targetMemberId);
}
