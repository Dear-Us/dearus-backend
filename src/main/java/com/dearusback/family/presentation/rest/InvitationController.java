package com.dearusback.family.presentation.rest;

import com.dearusback.family.application.port.in.GetInvitationByTokenUseCase;
import com.dearusback.family.application.port.in.AcceptInvitationUseCase;
import com.dearusback.family.application.port.in.ReadBabyProfileImageUrlUseCase;
import com.dearusback.family.presentation.dto.AcceptedInvitationResponse;
import com.dearusback.family.presentation.dto.InvitationPreviewResponse;
import com.dearusback.support.presentation.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invitations")
@Tag(name = "Invitation")
public class InvitationController {

    private final GetInvitationByTokenUseCase invitations;
    private final AcceptInvitationUseCase acceptInvitation;
    private final ReadBabyProfileImageUrlUseCase imageUrls;

    public InvitationController(GetInvitationByTokenUseCase invitations, AcceptInvitationUseCase acceptInvitation,
                                ReadBabyProfileImageUrlUseCase imageUrls) {
        this.invitations = invitations;
        this.acceptInvitation = acceptInvitation;
        this.imageUrls = imageUrls;
    }

    @GetMapping("/{token}")
    @Operation(operationId = "getInvitation", summary = "초대 토큰 미리보기", tags = "Invitation", description = "인증 없이 원문 토큰으로 초대 상태와 Baby 정보를 확인한다. 토큰의 존재 여부는 토큰 소지자에게만 공개되며 URL 공유 시 유출에 주의한다.")
    public ResponseEntity<ApiResponse<InvitationPreviewResponse>> get(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(
                InvitationPreviewResponse.from(invitations.get(token), imageUrls::readUrl)));
    }

    @PostMapping("/{token}/accept")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(operationId = "acceptInvitation", summary = "초대 수락", tags = "Invitation", description = "로그인한 활성 회원만 PENDING·미만료 초대를 한 번 수락할 수 있다. 기존 LEFT/REMOVED 관계는 ACTIVE CAREGIVER로 재활성화한다. 이미 ACTIVE 관계 또는 사용된 토큰은 409다. 마지막 선택 Baby가 null이면 초기화한다.")
    public ResponseEntity<ApiResponse<AcceptedInvitationResponse>> accept(
            @AuthenticationPrincipal Long memberId, @PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(AcceptedInvitationResponse.from(
                acceptInvitation.accept(memberId, token))));
    }
}
