package com.dearusback.family.presentation.rest;

import com.dearusback.family.application.port.in.CreateBabyInvitationUseCase;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.presentation.dto.CreateBabyInvitationResponse;
import com.dearusback.support.presentation.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/babies/{babyId}/invitations")
public class BabyInvitationController {

    private final CreateBabyInvitationUseCase invitations;

    public BabyInvitationController(CreateBabyInvitationUseCase invitations) {
        this.invitations = invitations;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateBabyInvitationResponse>> create(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId) {
        if (babyId == null || babyId <= 0) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_ID);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                CreateBabyInvitationResponse.from(invitations.create(memberId, babyId))));
    }
}
