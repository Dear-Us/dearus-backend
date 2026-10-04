package com.dearusback.identity.infrastructure.social;

import com.dearusback.identity.application.model.VerifiedSocialIdentity;
import com.dearusback.identity.application.port.out.SocialIdentityVerifier;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.exception.IdentityException;
import com.dearusback.identity.domain.type.SocialProvider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SocialIdentityVerifierAdapter implements SocialIdentityVerifier {

    private final Map<SocialProvider, SocialProviderClient> clients;

    public SocialIdentityVerifierAdapter(List<SocialProviderClient> clients) {
        this.clients = new EnumMap<>(SocialProvider.class);
        clients.forEach(client -> this.clients.put(client.provider(), client));
    }

    @Override
    public VerifiedSocialIdentity verify(SocialProvider provider, String providerToken) {
        SocialProviderClient client = clients.get(provider);
        if (client == null) {
            throw new IdentityException(IdentityErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);
        }
        return client.verify(providerToken);
    }
}
