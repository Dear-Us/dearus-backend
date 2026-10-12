package com.dearusback.family.presentation.rest;

import com.dearusback.family.application.port.in.GetInvitationByTokenUseCase;
import com.dearusback.family.application.port.in.AcceptInvitationUseCase;
import com.dearusback.family.presentation.dto.AcceptedInvitationResponse;
import com.dearusback.family.presentation.dto.InvitationPreviewResponse;
import com.dearusback.support.presentation.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {

    private final GetInvitationByTokenUseCase invitations;
    private final AcceptInvitationUseCase acceptInvitation;

    public InvitationController(GetInvitationByTokenUseCase invitations, AcceptInvitationUseCase acceptInvitation) {
        this.invitations = invitations;
        this.acceptInvitation = acceptInvitation;
    }

    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<InvitationPreviewResponse>> get(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(InvitationPreviewResponse.from(invitations.get(token))));
    }

    @PostMapping("/{token}/accept")
    public ResponseEntity<ApiResponse<AcceptedInvitationResponse>> accept(
            @AuthenticationPrincipal Long memberId, @PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(AcceptedInvitationResponse.from(
                acceptInvitation.accept(memberId, token))));
    }
}
