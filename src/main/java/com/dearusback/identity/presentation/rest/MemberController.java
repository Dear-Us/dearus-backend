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
    public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal Long memberId) {
        withdrawMemberUseCase.withdraw(memberId);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyProfileResponse>> me(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(MyProfileResponse.from(
                getMyProfileQuery.getMyProfile(memberId)
        )));
    }

    @PatchMapping(value = "/me/last-selected-baby", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<LastSelectedBabyResponse>> changeLastSelectedBaby(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ChangeLastSelectedBabyRequest request
    ) {
        Long selectedBabyId = changeLastSelectedBabyUseCase.change(
                new ChangeLastSelectedBabyCommand(memberId, request.babyId()));
        return ResponseEntity.ok(ApiResponse.success(new LastSelectedBabyResponse(selectedBabyId)));
    }

    @PatchMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
