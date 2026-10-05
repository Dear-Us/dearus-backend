package com.dearusback.family.application.port.in;

import com.dearusback.family.application.model.InvitationPreview;

public interface GetInvitationByTokenUseCase {
    InvitationPreview get(String rawToken);
}
