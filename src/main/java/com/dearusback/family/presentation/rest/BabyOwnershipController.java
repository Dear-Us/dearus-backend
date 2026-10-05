package com.dearusback.family.presentation.rest;

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
public class BabyOwnershipController {

    private final TransferBabyOwnershipUseCase transferOwnership;

    public BabyOwnershipController(TransferBabyOwnershipUseCase transferOwnership) {
        this.transferOwnership = transferOwnership;
    }

    @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
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
