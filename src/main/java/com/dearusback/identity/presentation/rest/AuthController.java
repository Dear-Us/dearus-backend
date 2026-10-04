package com.dearusback.identity.presentation.rest;

import com.dearusback.identity.application.model.SocialLoginCommand;
import com.dearusback.identity.application.model.SocialLoginResult;
import com.dearusback.identity.application.port.in.SocialLoginUseCase;
import com.dearusback.identity.domain.type.SocialProvider;
import com.dearusback.identity.presentation.dto.SocialLoginRequest;
import com.dearusback.identity.presentation.dto.SocialLoginResponse;
import com.dearusback.support.presentation.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final SocialLoginUseCase socialLoginUseCase;

    public AuthController(SocialLoginUseCase socialLoginUseCase) {
        this.socialLoginUseCase = socialLoginUseCase;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<SocialLoginResponse>> login(
            @Valid @RequestBody SocialLoginRequest request
    ) {
        SocialLoginCommand command = new SocialLoginCommand(
                SocialProvider.from(request.provider()),
                request.providerToken()
        );
        SocialLoginResult result = socialLoginUseCase.login(command);
        return ResponseEntity.ok(ApiResponse.success(SocialLoginResponse.from(result)));
    }
}
