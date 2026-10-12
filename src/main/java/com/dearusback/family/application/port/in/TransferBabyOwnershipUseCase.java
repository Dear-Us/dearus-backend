package com.dearusback.family.application.port.in;

import com.dearusback.family.application.model.TransferBabyOwnershipResult;

public interface TransferBabyOwnershipUseCase {
    TransferBabyOwnershipResult transfer(Long requesterId, Long babyId, Long targetMemberId);
}
