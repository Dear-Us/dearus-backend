package com.dearusback.family.presentation.rest;

import com.dearusback.family.application.model.BabyImageUpload;
import com.dearusback.family.application.model.CreateBabyCommand;
import com.dearusback.family.application.model.UpdateBabyCommand;
import com.dearusback.family.application.port.in.CreateBabyUseCase;
import com.dearusback.family.application.port.in.DeleteBabyUseCase;
import com.dearusback.family.application.port.in.GetBabyUseCase;
import com.dearusback.family.application.port.in.GetMyBabiesUseCase;
import com.dearusback.family.application.port.in.UpdateBabyUseCase;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.presentation.dto.BabyResponse;
import com.dearusback.family.presentation.dto.CreateBabyRequest;
import com.dearusback.family.presentation.dto.UpdateBabyRequest;
import com.dearusback.support.presentation.response.ApiResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/babies")
public class BabyController {

    private final CreateBabyUseCase useCase;
    private final GetMyBabiesUseCase getMyBabies;
    private final GetBabyUseCase getBaby;
    private final UpdateBabyUseCase updateBaby;
    private final DeleteBabyUseCase deleteBaby;

    public BabyController(CreateBabyUseCase useCase, GetMyBabiesUseCase getMyBabies, GetBabyUseCase getBaby,
                          UpdateBabyUseCase updateBaby, DeleteBabyUseCase deleteBaby) {
        this.useCase = useCase;
        this.getMyBabies = getMyBabies;
        this.getBaby = getBaby;
        this.updateBaby = updateBaby;
        this.deleteBaby = deleteBaby;
    }

    @DeleteMapping("/{babyId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId) {
        if (babyId == null || babyId <= 0) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_ID);
        }
        deleteBaby.delete(memberId, babyId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BabyResponse>>> list(@AuthenticationPrincipal Long memberId) {
        List<BabyResponse> babies = getMyBabies.getMyBabies(memberId).stream().map(BabyResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success(babies));
    }

    @GetMapping("/{babyId}")
    public ResponseEntity<ApiResponse<BabyResponse>> detail(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId) {
        if (babyId == null || babyId <= 0) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_ID);
        }
        return ResponseEntity.ok(ApiResponse.success(BabyResponse.from(getBaby.getBaby(memberId, babyId))));
    }

    @PatchMapping(value = "/{babyId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BabyResponse>> update(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId,
            @RequestPart("request") UpdateBabyRequest request,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage) {
        if (babyId == null || babyId <= 0) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_ID);
        }
        if (request == null || (profileImage != null && request.shouldRemoveProfileImage())) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_UPDATE);
        }
        BabyImageUpload image = null;
        if (profileImage != null) {
            if (profileImage.getSize() > BabyImageUpload.MAX_BYTES) {
                throw new FamilyException(FamilyErrorCode.BABY_IMAGE_TOO_LARGE);
            }
            try {
                image = BabyImageUpload.validate(profileImage.getBytes(), profileImage.getContentType());
            } catch (IOException exception) {
                throw new FamilyException(FamilyErrorCode.INVALID_BABY_IMAGE);
            }
        }
        return ResponseEntity.ok(ApiResponse.success(BabyResponse.from(updateBaby.update(new UpdateBabyCommand(
                memberId, babyId, request.babyName(), request.birthDate(), request.gender(),
                request.shouldRemoveProfileImage(), image)))));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BabyResponse>> create(
            @AuthenticationPrincipal Long memberId,
            @RequestPart("request") CreateBabyRequest request,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage
    ) {
        BabyImageUpload image = null;
        if (profileImage != null) {
            if (profileImage.getSize() > BabyImageUpload.MAX_BYTES) {
                throw new FamilyException(FamilyErrorCode.BABY_IMAGE_TOO_LARGE);
            }
            try {
                image = BabyImageUpload.validate(profileImage.getBytes(), profileImage.getContentType());
            } catch (IOException exception) {
                throw new FamilyException(FamilyErrorCode.INVALID_BABY_IMAGE);
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(BabyResponse.from(
                useCase.create(new CreateBabyCommand(memberId, request.babyName(), request.birthDate(),
                        request.gender(), image)))));
    }
}
