package com.dearusback.support.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "dearus.auth.google.client-id=")
@AutoConfigureMockMvc
class HealthEndpointTest {

    @Autowired MockMvc mvc;

    @Test
    void healthIsPublicButOtherActuatorAndBusinessEndpointsStayProtected() throws Exception {
        String health = mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(health).contains("\"status\":\"UP\"");

        mvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized());
    }
}
