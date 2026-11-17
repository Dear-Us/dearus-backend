package com.dearusback.identity.presentation.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dearusback.identity.application.model.LogoutCommand;
import com.dearusback.identity.application.port.in.LogoutUseCase;
import com.dearusback.support.presentation.exception.GlobalExceptionHandler;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LogoutControllerTest {

    @Test
    void successHasOnlyThreeApiResponseFieldsAndPassesTokenToUseCase() throws Exception {
        List<LogoutCommand> commands = new ArrayList<>();

        String response = mvc(commands::add).perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"opaque-token\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(commands).containsExactly(new LogoutCommand("opaque-token"));
        assertThat(response).isEqualTo("{\"success\":true,\"data\":null,\"message\":null}");
    }

    @Test
    void nullAndBlankRefreshTokensAreBadRequests() throws Exception {
        MockMvc mvc = mvc(command -> {
            throw new AssertionError("Logout use case must not be called");
        });

        for (String body : List.of("{\"refreshToken\":null}", "{}", "{\"refreshToken\":\"   \"}")) {
            mvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data").isEmpty())
                    .andExpect(jsonPath("$.message").value("잘못된 요청입니다."))
                    .andExpect(jsonPath("$.errorCode").doesNotExist());
        }
    }

    private MockMvc mvc(LogoutUseCase useCase) {
        return MockMvcBuilders.standaloneSetup(new AuthController(command -> null, () -> null, command -> null, useCase,
                        (memberId, imageUrl) -> imageUrl))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
