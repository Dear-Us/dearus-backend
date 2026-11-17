package com.dearusback.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소셜 SDK 인증 결과. provider와 providerToken을 함께 전달")
public record SocialLoginRequest(
        @NotBlank @Schema(description = "GOOGLE / KAKAO / NAVER. 이메일은 계정 병합 기준이 아님", example = "GOOGLE",
                allowableValues = {"GOOGLE", "KAKAO", "NAVER"}, requiredMode = Schema.RequiredMode.REQUIRED) String provider,
        @NotBlank @Schema(description = "GOOGLE은 ID Token, KAKAO/NAVER는 Access Token. 실제 값은 문서·로그에 남기지 말 것",
                example = "provider-token-from-sdk", requiredMode = Schema.RequiredMode.REQUIRED) String providerToken
) {
}
