package com.dearusback.identity.application.port.out;

import com.dearusback.identity.application.model.IssuedAccessToken;
import java.time.Instant;

public interface AccessTokenIssuer {

    IssuedAccessToken issue(Long memberId, Instant issuedAt);
}
