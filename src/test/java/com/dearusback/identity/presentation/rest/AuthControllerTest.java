package com.dearusback.identity.presentation.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dearusback.identity.application.model.SocialLoginResult;
import com.dearusback.identity.application.model.SocialLoginResult.MemberResult;
import com.dearusback.identity.application.port.in.SocialLoginUseCase;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.exception.IdentityException;
import com.dearusback.support.presentation.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    @Test
    void guestLoginRequiresNoProviderTokenAndReturnsTokens() throws Exception {
        SocialLoginResult guest = new SocialLoginResult(
                "guest-access", "guest-refresh", "Bearer", 1800, 2592000, true,
                new MemberResult(42L, "게스트", null, null)
        );

        MockMvcBuilders.standaloneSetup(new AuthController(command -> null, () -> guest,
                        command -> null, command -> {}, (memberId, imageUrl) -> imageUrl))
                .setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(post("/api/v1/auth/guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("guest-access"))
                .andExpect(jsonPath("$.data.refreshToken").value("guest-refresh"))
                .andExpect(jsonPath("$.data.isNewMember").value(true))
                .andExpect(jsonPath("$.data.member.memberId").value(42))
                .andExpect(jsonPath("$.data.member.memberName").value("게스트"));
    }

    @Test
    void returnsContractedLoginResponse() throws Exception {
        SocialLoginUseCase useCase = command -> new SocialLoginResult(
                "access-token",
                "refresh-token",
                "Bearer",
                1800,
                2592000,
                true,
                new MemberResult(1L, "김소은", null, null)
        );

        mockMvc(useCase).perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"GOOGLE","providerToken":"google-id-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessTokenExpiresInSeconds").value(1800))
                .andExpect(jsonPath("$.data.refreshTokenExpiresInSeconds").value(2592000))
                .andExpect(jsonPath("$.data.isNewMember").value(true))
                .andExpect(jsonPath("$.data.member.memberId").value(1))
                .andExpect(jsonPath("$.data.member.memberName").value("김소은"))
                .andExpect(jsonPath("$.data.member.profileImageUrl").isEmpty())
                .andExpect(jsonPath("$.data.member.lastSelectedBabyId").isEmpty())
                .andExpect(jsonPath("$.message").isEmpty());
    }

    @Test
    void rejectsUnsupportedProviderWithBadRequest() throws Exception {
        mockMvc(command -> null).perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"APPLE","providerToken":"token"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.message").value("지원하지 않는 소셜 로그인 제공자입니다."))
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    @Test
    void rejectsMalformedRequestWithBadRequest() throws Exception {
        mockMvc(command -> null).perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"GOOGLE","providerToken":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    void mapsInvalidTokenToUnauthorized() throws Exception {
        expectIdentityError(IdentityErrorCode.INVALID_SOCIAL_TOKEN, 401);
    }

    @Test
    void mapsWithdrawnMemberToForbidden() throws Exception {
        expectIdentityError(IdentityErrorCode.WITHDRAWN_MEMBER, 403);
    }

    @Test
    void mapsProviderFailureToServiceUnavailable() throws Exception {
        expectIdentityError(IdentityErrorCode.SOCIAL_PROVIDER_UNAVAILABLE, 503);
    }

    private void expectIdentityError(IdentityErrorCode errorCode, int status) throws Exception {
        SocialLoginUseCase useCase = command -> {
            throw new IdentityException(errorCode);
        };

        mockMvc(useCase).perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"GOOGLE","providerToken":"token"}
                                """))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.message").value(errorCode.message()))
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    private MockMvc mockMvc(SocialLoginUseCase useCase) {
        return MockMvcBuilders
                .standaloneSetup(new AuthController(useCase, () -> null, command -> null, command -> {},
                        (memberId, imageUrl) -> imageUrl))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
