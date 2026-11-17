package com.dearusback.family.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "OWNER로 지정할 기존 ACTIVE CAREGIVER")
public record TransferBabyOwnershipRequest(@Schema(description = "대상 공동 보호자 회원 ID, 양의 정수",
        example = "2", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED) Long memberId) {
}
