package com.dearusback.identity.infrastructure.social.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
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

class KakaoSocialClientTest {

    private MockRestServiceServer server;
    private KakaoSocialClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://kapi.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoSocialClient(builder.build(), 1234L);
    }

    @Test
    void verifiesTokenAppAndMapsNullableProfile() {
        server.expect(once(), requestTo("https://kapi.test/v1/user/access_token_info"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kakao-token"))
                .andRespond(withSuccess(
                        "{\"id\":123,\"expires_in\":100,\"app_id\":1234}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(once(), requestTo("https://kapi.test/v2/user/me"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kakao-token"))
                .andRespond(withSuccess(
                        "{\"id\":123,\"kakao_account\":{}}",
                        MediaType.APPLICATION_JSON
                ));

        VerifiedSocialIdentity identity = client.verify("kakao-token");

        assertThat(identity.providerUserId()).isEqualTo("123");
        assertThat(identity.email()).isNull();
        assertThat(identity.displayName()).isNull();
        assertThat(identity.profileImageUrl()).isNull();
        server.verify();
    }

    @Test
    void rejectsExpiredKakaoToken() {
        server.expect(requestTo("https://kapi.test/v1/user/access_token_info"))
                .andRespond(withSuccess(
                        "{\"id\":123,\"expires_in\":0,\"app_id\":1234}",
                        MediaType.APPLICATION_JSON
                ));

        assertErrorCode(() -> client.verify("expired-token"), IdentityErrorCode.INVALID_SOCIAL_TOKEN);
        server.verify();
    }

    @Test
    void mapsProviderServerFailureToUnavailable() {
        server.expect(requestTo("https://kapi.test/v1/user/access_token_info"))
                .andRespond(withServerError());

        assertErrorCode(() -> client.verify("token"), IdentityErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        server.verify();
    }

    private void assertErrorCode(Runnable operation, IdentityErrorCode expected) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
                        .isEqualTo(expected));
    }
}
