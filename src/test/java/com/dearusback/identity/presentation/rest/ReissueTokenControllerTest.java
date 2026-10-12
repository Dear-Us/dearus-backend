package com.dearusback.identity.presentation.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dearusback.identity.application.model.ReissueTokenResult;
import com.dearusback.identity.application.port.in.ReissueTokenUseCase;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.exception.IdentityException;
import com.dearusback.support.presentation.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReissueTokenControllerTest {

    @Test
    void successResponseContainsOnlyTokenFields() throws Exception {
        MockMvc mvc = mvc(command -> new ReissueTokenResult(
                "new-access", "new-refresh", "Bearer", 1800, 2592000
        ));

        mvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"old-refresh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessTokenExpiresInSeconds").value(1800))
                .andExpect(jsonPath("$.data.refreshTokenExpiresInSeconds").value(2592000))
                .andExpect(jsonPath("$.data.member").doesNotExist())
                .andExpect(jsonPath("$.data.isNewMember").doesNotExist())
                .andExpect(jsonPath("$.message").isEmpty());
    }

    @Test
    void invalidRefreshTokenReturnsOneGenericUnauthorizedMessage() throws Exception {
        MockMvc mvc = mvc(command -> {
            throw new IdentityException(IdentityErrorCode.INVALID_REFRESH_TOKEN);
        });

        mvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"unknown\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.message").value("인증 정보가 유효하지 않습니다."))
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    @Test
    void blankRefreshTokenIsBadRequest() throws Exception {
        mvc(command -> null).perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    private MockMvc mvc(ReissueTokenUseCase useCase) {
        return MockMvcBuilders.standaloneSetup(new AuthController(command -> null, useCase, command -> {}))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
