package com.dearusback.family.presentation.rest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.dearusback.family.application.port.in.TransferBabyOwnershipUseCase;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.presentation.dto.TransferBabyOwnershipRequest;
import com.dearusback.family.presentation.dto.TransferBabyOwnershipResponse;
import com.dearusback.support.presentation.response.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/babies/{babyId}/owner")
@Tag(name = "Baby Member")
@SecurityRequirement(name = "bearerAuth")
public class BabyOwnershipController {

    private final TransferBabyOwnershipUseCase transferOwnership;

    public BabyOwnershipController(TransferBabyOwnershipUseCase transferOwnership) {
        this.transferOwnership = transferOwnership;
    }

    @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "transferBabyOwnership", summary = "OWNER 권한 이전", tags = "Baby Member", description = "현재 ACTIVE OWNER가 같은 Baby의 ACTIVE CAREGIVER를 대상으로 지정한다. 하나의 트랜잭션에서 기존 OWNER는 CAREGIVER, 대상은 OWNER가 된다. 초대·보호자 관계 자체는 유지된다.")
    public ResponseEntity<ApiResponse<TransferBabyOwnershipResponse>> transfer(
            @AuthenticationPrincipal Long requesterId, @PathVariable Long babyId,
            @RequestBody TransferBabyOwnershipRequest request) {
        if (babyId == null || babyId <= 0) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_ID);
        }
        if (request == null || request.memberId() == null || request.memberId() <= 0) {
            throw new FamilyException(FamilyErrorCode.INVALID_MEMBER_ID);
        }
        return ResponseEntity.ok(ApiResponse.success(TransferBabyOwnershipResponse.from(
                transferOwnership.transfer(requesterId, babyId, request.memberId()))));
    }
}
