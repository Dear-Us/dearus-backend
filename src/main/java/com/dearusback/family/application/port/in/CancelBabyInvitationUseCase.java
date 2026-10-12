package com.dearusback.family.application.port.in;

public interface CancelBabyInvitationUseCase {
    void cancel(Long memberId, Long babyId, Long invitationId);
}
