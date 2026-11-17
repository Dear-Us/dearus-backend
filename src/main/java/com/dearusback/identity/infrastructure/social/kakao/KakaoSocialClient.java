package com.dearusback.identity.infrastructure.social.kakao;

import com.dearusback.identity.application.model.VerifiedSocialIdentity;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.exception.IdentityException;
import com.dearusback.identity.domain.type.SocialProvider;
import com.dearusback.identity.infrastructure.config.AuthProperties;
import com.dearusback.identity.infrastructure.social.SocialProviderClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KakaoSocialClient implements SocialProviderClient {

    private final RestClient restClient;
    private final long expectedAppId;

    @Autowired
    public KakaoSocialClient(RestClient.Builder restClientBuilder, AuthProperties properties) {
        this(
                restClientBuilder.clone().baseUrl(properties.kakao().baseUrl().toString()).build(),
                properties.kakao().appId()
        );
    }

    KakaoSocialClient(RestClient restClient, long expectedAppId) {
        this.restClient = restClient;
        this.expectedAppId = expectedAppId;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public VerifiedSocialIdentity verify(String providerToken) {
        if (expectedAppId == 0) {
            throw providerUnavailable();
        }
        try {
            KakaoTokenInfo tokenInfo = restClient.get()
                    .uri("/v1/user/access_token_info")
                    .headers(headers -> headers.setBearerAuth(providerToken))
                    .retrieve()
                    .body(KakaoTokenInfo.class);

            if (tokenInfo == null
                    || tokenInfo.id() == null
                    || tokenInfo.expiresIn() == null
                    || tokenInfo.expiresIn() <= 0
                    || tokenInfo.appId() == null
                    || tokenInfo.appId() != expectedAppId) {
                throw invalidToken();
            }

            KakaoUser user = restClient.get()
                    .uri("/v2/user/me")
                    .headers(headers -> headers.setBearerAuth(providerToken))
                    .retrieve()
                    .body(KakaoUser.class);

            if (user == null || user.id() == null || !user.id().equals(tokenInfo.id())) {
                throw invalidToken();
            }

            KakaoAccount account = user.kakaoAccount();
            KakaoProfile profile = account == null ? null : account.profile();
            return new VerifiedSocialIdentity(
                    SocialProvider.KAKAO,
                    user.id().toString(),
                    account == null ? null : account.email(),
                    profile == null ? null : profile.nickname(),
                    profile == null ? null : profile.profileImageUrl()
            );
        } catch (RestClientResponseException exception) {
            if (isUnavailable(exception)) {
                throw providerUnavailable();
            }
            throw invalidToken();
        } catch (ResourceAccessException exception) {
            throw providerUnavailable();
        }
    }

    private boolean isUnavailable(RestClientResponseException exception) {
        HttpStatusCode status = exception.getStatusCode();
        return status.is5xxServerError()
                || status.value() == 429
                || exception.getResponseBodyAsString().contains("\"code\":-1");
    }

    private IdentityException invalidToken() {
        return new IdentityException(IdentityErrorCode.INVALID_SOCIAL_TOKEN);
    }

    private IdentityException providerUnavailable() {
        return new IdentityException(IdentityErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoTokenInfo(
            Long id,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("app_id") Long appId
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoUser(
            Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoAccount(String email, KakaoProfile profile) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoProfile(
            String nickname,
            @JsonProperty("profile_image_url") String profileImageUrl
    ) {
    }
}
