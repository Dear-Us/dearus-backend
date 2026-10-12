package com.dearusback.family.application.port.in;

import com.dearusback.family.application.model.AcceptedInvitation;

public interface AcceptInvitationUseCase {
    AcceptedInvitation accept(Long memberId, String rawToken);
}
