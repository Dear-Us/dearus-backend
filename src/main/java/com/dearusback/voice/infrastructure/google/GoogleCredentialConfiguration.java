package com.dearusback.voice.infrastructure.google;

import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional(GoogleProviderEnabledCondition.class)
class GoogleCredentialConfiguration {
    @Bean
    GoogleCredentials dearusGoogleCredentials(
            @Value("${dearus.voice.google.credentials-json:}") String credentialsJson) {
        return GoogleCredentialFactory.load(credentialsJson);
    }
}
