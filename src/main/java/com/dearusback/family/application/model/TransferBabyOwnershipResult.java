package com.dearusback.family.application.model;

public record TransferBabyOwnershipResult(Long babyId, Long previousOwnerMemberId, Long ownerMemberId) {
}
