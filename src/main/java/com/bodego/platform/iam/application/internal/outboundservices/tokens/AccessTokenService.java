package com.bodego.platform.iam.application.internal.outboundservices.tokens;

import com.bodego.platform.shared.application.security.AuthenticatedIdentity;

public interface AccessTokenService {
    IssuedAccessToken issue(AuthenticatedIdentity identity);

    record IssuedAccessToken(String accessToken, String tokenType, long expiresIn) {
        @Override public String toString() { return "IssuedAccessToken[REDACTED]"; }
    }
}
