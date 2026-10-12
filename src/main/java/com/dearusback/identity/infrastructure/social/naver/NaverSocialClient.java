package com.dearusback.identity.infrastructure.social.naver;

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
public class NaverSocialClient implements SocialProviderClient {

    private final RestClient restClient;

    @Autowired
    public NaverSocialClient(RestClient.Builder restClientBuilder, AuthProperties properties) {
        this(restClientBuilder.clone().baseUrl(properties.naver().baseUrl().toString()).build());
    }

    NaverSocialClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.NAVER;
    }

    @Override
    public VerifiedSocialIdentity verify(String providerToken) {
        try {
            NaverResponse result = restClient.get()
                    .uri("/v1/nid/me")
                    .headers(headers -> headers.setBearerAuth(providerToken))
                    .retrieve()
                    .body(NaverResponse.class);

            NaverProfile profile = result == null ? null : result.response();
            if (result == null
                    || !"00".equals(result.resultCode())
                    || profile == null
                    || profile.id() == null
                    || profile.id().isBlank()) {
                throw invalidToken();
            }

            return new VerifiedSocialIdentity(
                    SocialProvider.NAVER,
                    profile.id(),
                    profile.email(),
                    profile.nickname(),
                    profile.profileImage()
            );
        } catch (RestClientResponseException exception) {
            if (isUnavailable(exception.getStatusCode())) {
                throw providerUnavailable();
            }
            throw invalidToken();
        } catch (ResourceAccessException exception) {
            throw providerUnavailable();
        }
    }

    private boolean isUnavailable(HttpStatusCode status) {
        return status.is5xxServerError() || status.value() == 429;
    }

    private IdentityException invalidToken() {
        return new IdentityException(IdentityErrorCode.INVALID_SOCIAL_TOKEN);
    }

    private IdentityException providerUnavailable() {
        return new IdentityException(IdentityErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NaverResponse(
            @JsonProperty("resultcode") String resultCode,
            NaverProfile response
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NaverProfile(
            String id,
            String email,
            String nickname,
            @JsonProperty("profile_image") String profileImage
    ) {
    }
}
