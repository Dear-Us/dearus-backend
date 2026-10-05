package com.dearusback.family.presentation.rest;

import com.dearusback.family.application.port.in.LeaveBabyCaregiverUseCase;
import com.dearusback.family.application.port.in.RemoveBabyCaregiverUseCase;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.support.presentation.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/babies/{babyId}/members")
public class BabyMemberManagementController {

    private final RemoveBabyCaregiverUseCase removeCaregiver;
    private final LeaveBabyCaregiverUseCase leaveCaregiver;

    public BabyMemberManagementController(RemoveBabyCaregiverUseCase removeCaregiver,
                                          LeaveBabyCaregiverUseCase leaveCaregiver) {
        this.removeCaregiver = removeCaregiver;
        this.leaveCaregiver = leaveCaregiver;
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<ApiResponse<Void>> remove(@AuthenticationPrincipal Long requesterId,
                                                     @PathVariable Long babyId, @PathVariable Long memberId) {
        if (babyId == null || babyId <= 0) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_ID);
        }
        if (memberId == null || memberId <= 0) {
            throw new FamilyException(FamilyErrorCode.INVALID_MEMBER_ID);
        }
        removeCaregiver.remove(requesterId, babyId, memberId);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> leave(@AuthenticationPrincipal Long memberId,
                                                    @PathVariable Long babyId) {
        if (babyId == null || babyId <= 0) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_ID);
        }
        leaveCaregiver.leave(memberId, babyId);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }
}
