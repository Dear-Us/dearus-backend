package com.dearusback.family.application.port.in;

import com.dearusback.family.application.model.CreatedInvitation;

public interface CreateBabyInvitationUseCase {
    CreatedInvitation create(Long memberId, Long babyId);
}
