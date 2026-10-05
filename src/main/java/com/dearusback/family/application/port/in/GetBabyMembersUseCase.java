package com.dearusback.family.application.port.in;

import com.dearusback.family.application.model.BabyMemberSummary;
import java.util.List;

public interface GetBabyMembersUseCase {
    List<BabyMemberSummary> getMembers(Long memberId, Long babyId);
}
