package com.dearusback.family.presentation.rest;

import com.dearusback.family.application.port.in.CreateBabyInvitationUseCase;
import com.dearusback.family.application.port.in.ListBabyInvitationsUseCase;
import com.dearusback.family.application.port.in.CancelBabyInvitationUseCase;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.presentation.dto.CreateBabyInvitationResponse;
import com.dearusback.family.presentation.dto.InvitationSummaryResponse;
import com.dearusback.support.presentation.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Invitation", description = "72시간 유효한 단일 사용 공동 보호자 초대")
@SecurityRequirement(name = "bearerAuth")
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
    @Operation(operationId = "createInvitation", summary = "공동 보호자 초대 링크 생성", tags = "Invitation", description = "ACTIVE OWNER만 생성한다. CAREGIVER 역할의 72시간 유효한 단일 사용 초대를 만들고 invitationUrl을 반환한다. 원문 토큰은 서버 DB에 저장하지 않는다.")
    public ResponseEntity<ApiResponse<CreateBabyInvitationResponse>> create(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId) {
        if (babyId == null || babyId <= 0) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_ID);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                CreateBabyInvitationResponse.from(invitations.create(memberId, babyId))));
    }

    @GetMapping
    @Operation(operationId = "listInvitations", summary = "초대 목록 조회", tags = "Invitation", description = "ACTIVE OWNER가 해당 Baby의 초대 목록과 PENDING/ACCEPTED/CANCELED/EXPIRED 상태를 조회한다. 원문 토큰과 URL은 목록에 포함되지 않는다.")
    public ResponseEntity<ApiResponse<List<InvitationSummaryResponse>>> list(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId) {
        if (babyId == null || babyId <= 0) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_ID);
        }
        return ResponseEntity.ok(ApiResponse.success(listInvitations.list(memberId, babyId).stream()
                .map(InvitationSummaryResponse::from).toList()));
    }

    @DeleteMapping("/{invitationId}")
    @Operation(operationId = "cancelInvitation", summary = "초대 취소", tags = "Invitation", description = "ACTIVE OWNER가 PENDING 초대만 취소한다. 이미 수락·취소·만료된 초대는 상태 충돌로 처리한다.")
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
