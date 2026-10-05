package com.dearusback.family.application.port.in;

import com.dearusback.family.application.model.InvitationSummary;
import java.util.List;

public interface ListBabyInvitationsUseCase {
    List<InvitationSummary> list(Long memberId, Long babyId);
}
