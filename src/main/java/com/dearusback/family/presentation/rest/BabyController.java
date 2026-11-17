package com.dearusback.family.presentation.rest;

import com.dearusback.family.application.model.BabyImageUpload;
import com.dearusback.family.application.model.CreateBabyCommand;
import com.dearusback.family.application.model.UpdateBabyCommand;
import com.dearusback.family.application.port.in.CreateBabyUseCase;
import com.dearusback.family.application.port.in.DeleteBabyUseCase;
import com.dearusback.family.application.port.in.GetBabyUseCase;
import com.dearusback.family.application.port.in.GetBabyMembersUseCase;
import com.dearusback.family.application.port.in.GetMyBabiesUseCase;
import com.dearusback.family.application.port.in.UpdateBabyUseCase;
import com.dearusback.family.application.port.in.ReadBabyProfileImageUrlUseCase;
import com.dearusback.identity.application.port.in.ReadMemberProfileImageUrlUseCase;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.presentation.dto.BabyResponse;
import com.dearusback.family.presentation.dto.BabyMemberResponse;
import com.dearusback.family.presentation.dto.CreateBabyRequest;
import com.dearusback.family.presentation.dto.UpdateBabyRequest;
import com.dearusback.support.presentation.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/babies")
@Tag(name = "Baby", description = "아기 생성·조회·수정·삭제")
@SecurityRequirement(name = "bearerAuth")
public class BabyController {

    private final CreateBabyUseCase useCase;
    private final GetMyBabiesUseCase getMyBabies;
    private final GetBabyUseCase getBaby;
    private final GetBabyMembersUseCase getBabyMembers;
    private final UpdateBabyUseCase updateBaby;
    private final DeleteBabyUseCase deleteBaby;
    private final ReadBabyProfileImageUrlUseCase babyImageUrls;
    private final ReadMemberProfileImageUrlUseCase memberImageUrls;

    public BabyController(CreateBabyUseCase useCase, GetMyBabiesUseCase getMyBabies, GetBabyUseCase getBaby,
                          GetBabyMembersUseCase getBabyMembers, UpdateBabyUseCase updateBaby,
                          DeleteBabyUseCase deleteBaby, ReadBabyProfileImageUrlUseCase babyImageUrls,
                          ReadMemberProfileImageUrlUseCase memberImageUrls) {
        this.useCase = useCase;
        this.getMyBabies = getMyBabies;
        this.getBaby = getBaby;
        this.getBabyMembers = getBabyMembers;
        this.updateBaby = updateBaby;
        this.deleteBaby = deleteBaby;
        this.babyImageUrls = babyImageUrls;
        this.memberImageUrls = memberImageUrls;
    }

    @GetMapping("/{babyId}/members")
    @Operation(operationId = "listBabyMembers", summary = "공동 보호자 목록 조회", tags = "Baby Member", description = "해당 Baby의 ACTIVE 공동 보호자만 조회한다. 현재 Baby에 접근할 수 있는 회원만 호출할 수 있다. OWNER/CAREGIVER 역할과 프로필을 반환한다.")
    public ResponseEntity<ApiResponse<List<BabyMemberResponse>>> members(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId) {
        if (babyId == null || babyId <= 0) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_ID);
        }
        return ResponseEntity.ok(ApiResponse.success(getBabyMembers.getMembers(memberId, babyId)
                .stream().map(summary -> BabyMemberResponse.from(summary, memberImageUrls::readUrl)).toList()));
    }

    @DeleteMapping("/{babyId}")
    @Operation(operationId = "deleteBaby", summary = "아기 삭제", tags = "Baby", description = "ACTIVE OWNER만 삭제할 수 있다. Baby는 soft delete되며 관련 보호자 관계가 종료되고 이 Baby를 선택 중이던 회원의 lastSelectedBabyId가 해제된다.")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId) {
        if (babyId == null || babyId <= 0) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_ID);
        }
        deleteBaby.delete(memberId, babyId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping
    @Operation(operationId = "listBabies", summary = "내 아기 목록 조회", tags = "Baby", description = "현재 회원이 ACTIVE 보호자로 연결된 삭제되지 않은 Baby 목록을 반환한다. 각 항목에 myRole이 포함된다.")
    public ResponseEntity<ApiResponse<List<BabyResponse>>> list(@AuthenticationPrincipal Long memberId) {
        List<BabyResponse> babies = getMyBabies.getMyBabies(memberId).stream()
                .map(summary -> BabyResponse.from(summary, babyImageUrls::readUrl)).toList();
        return ResponseEntity.ok(ApiResponse.success(babies));
    }

    @GetMapping("/{babyId}")
    @Operation(operationId = "getBaby", summary = "아기 상세 조회", tags = "Baby", description = "ACTIVE 공동 보호자만 접근한다. 접근할 수 없는 Baby와 삭제된 Baby는 404로 응답한다.")
    public ResponseEntity<ApiResponse<BabyResponse>> detail(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId) {
        if (babyId == null || babyId <= 0) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_ID);
        }
        return ResponseEntity.ok(ApiResponse.success(
                BabyResponse.from(getBaby.getBaby(memberId, babyId), babyImageUrls::readUrl)));
    }

    @PatchMapping(value = "/{babyId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Hidden
    public ResponseEntity<ApiResponse<BabyResponse>> updateByUrl(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId,
            @RequestBody UpdateBabyRequest request) {
        return update(memberId, babyId, request, null);
    }

    @PatchMapping(value = "/{babyId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "updateBaby", summary = "아기 정보 수정", tags = "Baby", description = "ACTIVE OWNER만 수정한다. multipart의 request part는 application/json, profileImage part는 선택 사항이다. JSON 요청 본문으로 profileImageUrl을 전달할 수도 있다. 생략한 필드는 유지되고 removeProfileImage=true는 이미지를 제거한다. URL·제거·업로드는 동시에 사용할 수 없다. JPEG/PNG/WebP, 최대 5 MiB.")
    public ResponseEntity<ApiResponse<BabyResponse>> update(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId,
            @RequestPart("request") UpdateBabyRequest request,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage) {
        if (babyId == null || babyId <= 0) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_ID);
        }
        if (request == null || (profileImage != null && request.shouldRemoveProfileImage())
                || (request.profileImageUrl() != null
                && (profileImage != null || request.shouldRemoveProfileImage()))) {
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
                request.shouldRemoveProfileImage(), image, request.profileImageUrl())), babyImageUrls::readUrl)));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Hidden
    public ResponseEntity<ApiResponse<BabyResponse>> createByUrl(
            @AuthenticationPrincipal Long memberId, @RequestBody CreateBabyRequest request) {
        return create(memberId, request, null);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "createBaby", summary = "아기 등록", tags = "Baby", description = "multipart의 필수 request JSON part에 babyName, YYYY-MM-DD birthDate, gender를 담는다. profileImage는 선택적 파일 part다. JSON 요청 본문으로 profileImageUrl을 전달할 수도 있다. URL과 파일은 동시에 사용할 수 없다. JPEG/PNG/WebP, 최대 5 MiB. 생성자는 OWNER가 되고 lastSelectedBabyId가 null이면 새 Baby로 초기화된다.")
    public ResponseEntity<ApiResponse<BabyResponse>> create(
            @AuthenticationPrincipal Long memberId,
            @RequestPart("request") CreateBabyRequest request,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage
    ) {
        if (request == null || (profileImage != null && request.profileImageUrl() != null)) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_IMAGE);
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
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(BabyResponse.from(
                useCase.create(new CreateBabyCommand(memberId, request.babyName(), request.birthDate(),
                        request.gender(), image, request.profileImageUrl())), babyImageUrls::readUrl)));
    }
}
