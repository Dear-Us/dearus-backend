package com.dearusback.identity.infrastructure.social;

import com.dearusback.identity.application.model.VerifiedSocialIdentity;
import com.dearusback.identity.domain.type.SocialProvider;

public interface SocialProviderClient {

    SocialProvider provider();

    VerifiedSocialIdentity verify(String providerToken);
}
