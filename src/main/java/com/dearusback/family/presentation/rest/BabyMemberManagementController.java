package com.dearusback.family.presentation.rest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(name = "Baby Member")
@SecurityRequirement(name = "bearerAuth")
public class BabyMemberManagementController {

    private final RemoveBabyCaregiverUseCase removeCaregiver;
    private final LeaveBabyCaregiverUseCase leaveCaregiver;

    public BabyMemberManagementController(RemoveBabyCaregiverUseCase removeCaregiver,
                                          LeaveBabyCaregiverUseCase leaveCaregiver) {
        this.removeCaregiver = removeCaregiver;
        this.leaveCaregiver = leaveCaregiver;
    }

    @DeleteMapping("/{memberId}")
    @Operation(operationId = "removeBabyCaregiver", summary = "공동 보호자 내보내기", tags = "Baby Member", description = "ACTIVE OWNER가 다른 ACTIVE CAREGIVER를 REMOVED 상태로 종료한다. OWNER는 내보낼 수 없다. 대상의 마지막 선택 Baby가 해당 Baby이면 null로 바뀐다.")
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
    @Operation(operationId = "leaveBaby", summary = "공동 보호자 나가기", tags = "Baby Member", description = "ACTIVE CAREGIVER가 본인 관계를 LEFT 상태로 종료한다. OWNER는 나갈 수 없다. 해당 Baby가 마지막 선택이라면 null로 해제한다.")
    public ResponseEntity<ApiResponse<Void>> leave(@AuthenticationPrincipal Long memberId,
                                                    @PathVariable Long babyId) {
        if (babyId == null || babyId <= 0) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_ID);
        }
        leaveCaregiver.leave(memberId, babyId);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }
}
