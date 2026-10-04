package com.dearusback.identity.infrastructure.social.naver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.dearusback.identity.application.model.VerifiedSocialIdentity;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.support.domain.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NaverSocialClientTest {

    private MockRestServiceServer server;
    private NaverSocialClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://naver.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NaverSocialClient(builder.build());
    }

    @Test
    void usesNaverResponseIdAsProviderUserId() {
        server.expect(requestTo("https://naver.test/v1/nid/me"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer naver-token"))
                .andRespond(withSuccess("""
                        {
                          "resultcode":"00",
                          "message":"success",
                          "response":{
                            "id":"naver-user-1",
                            "email":"user@example.com",
                            "nickname":"네이버 사용자",
                            "profile_image":"https://image.example/naver.png"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        VerifiedSocialIdentity identity = client.verify("naver-token");

        assertThat(identity.providerUserId()).isEqualTo("naver-user-1");
        assertThat(identity.email()).isEqualTo("user@example.com");
        assertThat(identity.displayName()).isEqualTo("네이버 사용자");
        server.verify();
    }

    @Test
    void mapsNaverAuthenticationFailureToInvalidToken() {
        server.expect(requestTo("https://naver.test/v1/nid/me"))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.verify("invalid-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
                        .isEqualTo(IdentityErrorCode.INVALID_SOCIAL_TOKEN));
        server.verify();
    }
}
