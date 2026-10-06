package com.dearusback.identity.presentation.rest;

import com.dearusback.identity.application.model.ChangeLastSelectedBabyCommand;
import com.dearusback.identity.application.model.ProfileImageUpload;
import com.dearusback.identity.application.model.UpdateMyProfileCommand;
import com.dearusback.identity.application.port.in.ChangeLastSelectedBabyUseCase;
import com.dearusback.identity.application.port.in.GetMyProfileQuery;
import com.dearusback.identity.application.port.in.UpdateMyProfileUseCase;
import com.dearusback.identity.application.port.in.WithdrawMemberUseCase;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.exception.IdentityException;
import com.dearusback.identity.presentation.dto.ChangeLastSelectedBabyRequest;
import com.dearusback.identity.presentation.dto.LastSelectedBabyResponse;
import com.dearusback.identity.presentation.dto.MyProfileResponse;
import com.dearusback.identity.presentation.dto.UpdateMyProfileRequest;
import com.dearusback.support.presentation.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/members")
@Tag(name = "Member", description = "내 회원 정보와 마지막 선택 아기")
@SecurityRequirement(name = "bearerAuth")
public class MemberController {

    private final GetMyProfileQuery getMyProfileQuery;
    private final UpdateMyProfileUseCase updateMyProfileUseCase;
    private final ChangeLastSelectedBabyUseCase changeLastSelectedBabyUseCase;
    private final WithdrawMemberUseCase withdrawMemberUseCase;

    public MemberController(GetMyProfileQuery getMyProfileQuery, UpdateMyProfileUseCase updateMyProfileUseCase,
                            ChangeLastSelectedBabyUseCase changeLastSelectedBabyUseCase,
                            WithdrawMemberUseCase withdrawMemberUseCase) {
        this.getMyProfileQuery = getMyProfileQuery;
        this.updateMyProfileUseCase = updateMyProfileUseCase;
        this.changeLastSelectedBabyUseCase = changeLastSelectedBabyUseCase;
        this.withdrawMemberUseCase = withdrawMemberUseCase;
    }

    @DeleteMapping("/me")
    @Operation(operationId = "withdrawMember", summary = "회원 탈퇴", tags = "Member", description = "ACTIVE OWNER인 Baby가 남아 있으면 409로 거절한다. 회원은 soft delete되며 Refresh 세션은 폐기된다. 탈퇴 계정의 이후 접근은 금지된다.")
    public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal Long memberId) {
        withdrawMemberUseCase.withdraw(memberId);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @GetMapping("/me")
    @Operation(operationId = "getMyProfile", summary = "내 회원 정보 조회", tags = "Member", description = "활성 회원의 이메일, 이름, 이미지 URL, IANA timezone, 마지막 선택 Baby, 연결된 소셜 계정을 조회한다. lastSelectedBabyId와 profileImageUrl은 null일 수 있다.")
    public ResponseEntity<ApiResponse<MyProfileResponse>> me(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(MyProfileResponse.from(
                getMyProfileQuery.getMyProfile(memberId)
        )));
    }

    @PatchMapping(value = "/me/last-selected-baby", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "changeLastSelectedBaby", summary = "마지막 선택 아기 변경", tags = "Member", description = "현재 회원이 ACTIVE 공동 보호자인 Baby만 선택할 수 있다. Baby 삭제·보호자 관계 종료 시 선택 값이 null로 정리될 수 있다.")
    public ResponseEntity<ApiResponse<LastSelectedBabyResponse>> changeLastSelectedBaby(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ChangeLastSelectedBabyRequest request
    ) {
        Long selectedBabyId = changeLastSelectedBabyUseCase.change(
                new ChangeLastSelectedBabyCommand(memberId, request.babyId()));
        return ResponseEntity.ok(ApiResponse.success(new LastSelectedBabyResponse(selectedBabyId)));
    }

    @PatchMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "updateMyProfile", summary = "내 정보 수정", tags = "Member", description = "multipart/form-data: application/json인 필수 request part와 선택 profileImage part. memberName을 생략하면 이름 유지, removeProfileImage=true이면 이미지 제거, 이미지 part를 제공하면 교체한다. 제거 플래그와 이미지 업로드 동시 사용은 400. JPEG/PNG/WebP, 최대 5 MiB. 변경할 필드가 하나도 없으면 400.")
    public ResponseEntity<ApiResponse<MyProfileResponse>> updateMe(
            @AuthenticationPrincipal Long memberId,
            @RequestPart("request") UpdateMyProfileRequest request,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage
    ) {
        if (profileImage != null && request.shouldRemoveProfileImage()) {
            throw new IdentityException(IdentityErrorCode.INVALID_PROFILE_UPDATE);
        }
        ProfileImageUpload upload = null;
        if (profileImage != null) {
            if (profileImage.getSize() > ProfileImageUpload.MAX_BYTES) {
                throw new IdentityException(IdentityErrorCode.PROFILE_IMAGE_TOO_LARGE);
            }
            try {
                upload = ProfileImageUpload.validate(profileImage.getBytes(), profileImage.getContentType());
            } catch (IOException exception) {
                throw new IdentityException(IdentityErrorCode.INVALID_PROFILE_IMAGE);
            }
        }
        UpdateMyProfileCommand command = new UpdateMyProfileCommand(
                memberId, request.memberName(), request.shouldRemoveProfileImage(), upload
        );
        return ResponseEntity.ok(ApiResponse.success(MyProfileResponse.from(updateMyProfileUseCase.update(command))));
    }
}
