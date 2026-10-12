package com.dearusback.family.presentation.rest;

import com.dearusback.family.application.port.in.CreateBabyInvitationUseCase;
import com.dearusback.family.application.port.in.ListBabyInvitationsUseCase;
import com.dearusback.family.application.port.in.CancelBabyInvitationUseCase;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.presentation.dto.CreateBabyInvitationResponse;
import com.dearusback.family.presentation.dto.InvitationSummaryResponse;
import com.dearusback.support.presentation.response.ApiResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/babies/{babyId}/invitations")
public class BabyInvitationController {

    private final CreateBabyInvitationUseCase invitations;
    private final ListBabyInvitationsUseCase listInvitations;
    private final CancelBabyInvitationUseCase cancelInvitation;

    public BabyInvitationController(CreateBabyInvitationUseCase invitations,
                                    ListBabyInvitationsUseCase listInvitations,
                                    CancelBabyInvitationUseCase cancelInvitation) {
        this.invitations = invitations;
        this.listInvitations = listInvitations;
        this.cancelInvitation = cancelInvitation;
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

    @GetMapping
    public ResponseEntity<ApiResponse<List<InvitationSummaryResponse>>> list(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId) {
        if (babyId == null || babyId <= 0) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_ID);
        }
        return ResponseEntity.ok(ApiResponse.success(listInvitations.list(memberId, babyId).stream()
                .map(InvitationSummaryResponse::from).toList()));
    }

    @DeleteMapping("/{invitationId}")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId,
            @PathVariable Long invitationId) {
        if (babyId == null || babyId <= 0) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_ID);
        }
        if (invitationId == null || invitationId <= 0) {
            throw new FamilyException(FamilyErrorCode.INVALID_INVITATION_ID);
        }
        cancelInvitation.cancel(memberId, babyId, invitationId);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }
}
