package com.dearusback.family.presentation.rest;

import com.dearusback.family.application.model.BabyImageUpload;
import com.dearusback.family.application.model.CreateBabyCommand;
import com.dearusback.family.application.port.in.CreateBabyUseCase;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.presentation.dto.CreateBabyRequest;
import com.dearusback.family.presentation.dto.CreateBabyResponse;
import com.dearusback.support.presentation.response.ApiResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/babies")
public class BabyController {

    private final CreateBabyUseCase useCase;

    public BabyController(CreateBabyUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CreateBabyResponse>> create(
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
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(CreateBabyResponse.from(
                useCase.create(new CreateBabyCommand(memberId, request.babyName(), request.birthDate(),
                        request.gender(), image)))));
    }
}
