package com.dearusback.identity.application.port.out;

import com.dearusback.identity.application.model.VerifiedSocialIdentity;
import com.dearusback.identity.domain.type.SocialProvider;

public interface SocialIdentityVerifier {

    VerifiedSocialIdentity verify(SocialProvider provider, String providerToken);
}
