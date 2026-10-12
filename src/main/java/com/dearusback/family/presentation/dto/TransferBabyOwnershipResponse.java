package com.dearusback.family.presentation.dto;

import com.dearusback.family.application.model.TransferBabyOwnershipResult;

public record TransferBabyOwnershipResponse(Long babyId, Long previousOwnerMemberId, Long ownerMemberId) {
    public static TransferBabyOwnershipResponse from(TransferBabyOwnershipResult result) {
        return new TransferBabyOwnershipResponse(result.babyId(), result.previousOwnerMemberId(),
                result.ownerMemberId());
    }
}
