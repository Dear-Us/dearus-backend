package com.dearusback.identity.presentation.rest;

import com.dearusback.identity.application.model.SocialLoginCommand;
import com.dearusback.identity.application.model.SocialLoginResult;
import com.dearusback.identity.application.model.ReissueTokenCommand;
import com.dearusback.identity.application.model.LogoutCommand;
import com.dearusback.identity.application.port.in.LogoutUseCase;
import com.dearusback.identity.application.port.in.GuestLoginUseCase;
import com.dearusback.identity.application.port.in.SocialLoginUseCase;
import com.dearusback.identity.application.port.in.ReissueTokenUseCase;
import com.dearusback.identity.application.port.in.ReadMemberProfileImageUrlUseCase;
import com.dearusback.identity.domain.type.SocialProvider;
import com.dearusback.identity.presentation.dto.ReissueTokenRequest;
import com.dearusback.identity.presentation.dto.LogoutRequest;
import com.dearusback.identity.presentation.dto.ReissueTokenResponse;
import com.dearusback.identity.presentation.dto.SocialLoginRequest;
import com.dearusback.identity.presentation.dto.SocialLoginResponse;
import com.dearusback.support.presentation.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "소셜·게스트 로그인, Refresh Token 회전, 현재 세션 로그아웃")
public class AuthController {

    private final SocialLoginUseCase socialLoginUseCase;
    private final GuestLoginUseCase guestLoginUseCase;
    private final ReissueTokenUseCase reissueTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final ReadMemberProfileImageUrlUseCase imageUrls;

    public AuthController(
            SocialLoginUseCase socialLoginUseCase,
            GuestLoginUseCase guestLoginUseCase,
            ReissueTokenUseCase reissueTokenUseCase,
            LogoutUseCase logoutUseCase,
            ReadMemberProfileImageUrlUseCase imageUrls
    ) {
        this.socialLoginUseCase = socialLoginUseCase;
        this.guestLoginUseCase = guestLoginUseCase;
        this.reissueTokenUseCase = reissueTokenUseCase;
        this.logoutUseCase = logoutUseCase;
        this.imageUrls = imageUrls;
    }

    @PostMapping("/guest")
    @Operation(operationId = "loginGuest", summary = "게스트 로그인", tags = "Auth", description = "본문과 소셜 Provider 토큰 없이 호출한다. 호출마다 독립적인 게스트 회원을 생성하고 DearUs Access JWT와 일회성 Refresh Token을 발급한다. 앱은 두 토큰을 보안 저장소에 보관하고 재실행 시 Refresh Token 재발급을 사용한다. 게스트 데이터는 추후 소셜 로그인 계정으로 자동 승계되지 않는다. 인증 없이 호출한다.")
    public ResponseEntity<ApiResponse<SocialLoginResponse>> guest() {
        return ResponseEntity.ok(ApiResponse.success(
                SocialLoginResponse.from(guestLoginUseCase.loginGuest(), imageUrls::readUrl)
        ));
    }

    @PostMapping("/login")
    @Operation(operationId = "login", summary = "소셜 로그인", tags = "Auth", description = "Google ID Token 또는 Kakao/Naver Access Token을 providerToken으로 전달한다. 서버가 Provider를 검증하고 (provider, providerUserId)로 계정을 찾거나 생성한다. 이메일이 같아도 자동 병합하지 않는다. 성공하면 DearUs Access JWT와 일회성 Refresh Token을 발급한다. 인증 없이 호출한다.")
    public ResponseEntity<ApiResponse<SocialLoginResponse>> login(
            @Valid @RequestBody SocialLoginRequest request
    ) {
        SocialLoginCommand command = new SocialLoginCommand(
                SocialProvider.from(request.provider()),
                request.providerToken()
        );
        SocialLoginResult result = socialLoginUseCase.login(command);
        return ResponseEntity.ok(ApiResponse.success(SocialLoginResponse.from(result, imageUrls::readUrl)));
    }

    @PostMapping("/reissue")
    @Operation(operationId = "reissueToken", summary = "토큰 재발급", tags = "Auth", description = "Refresh Token 원문을 JSON body로 전달한다. 사용 중인 토큰을 원자적으로 revoke하고 새 Access/Refresh Token을 발급한다. 같은 Refresh Token은 한 번만 회전할 수 있으므로 앱은 동시 401을 단일 재발급으로 묶어야 한다. Access JWT는 필요 없다.")
    public ResponseEntity<ApiResponse<ReissueTokenResponse>> reissue(
            @Valid @RequestBody ReissueTokenRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                ReissueTokenResponse.from(reissueTokenUseCase.reissue(new ReissueTokenCommand(request.refreshToken())))
        ));
    }

    @PostMapping("/logout")
    @Operation(operationId = "logout", summary = "현재 세션 로그아웃", tags = "Auth", description = "body의 Refresh Token에 해당하는 세션만 revoke한다. 이미 revoke된 토큰도 동일하게 성공한다. 다른 기기 세션과 이미 발급된 Access JWT는 즉시 무효화하지 않는다. Access JWT는 필요 없다.")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        logoutUseCase.logout(new LogoutCommand(request.refreshToken()));
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }
}
