package com.bodego.platform.iam.infrastructure.tokens;

import com.bodego.platform.iam.application.internal.outboundservices.tokens.AccessTokenService;
import com.bodego.platform.shared.application.security.AuthenticatedIdentity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class JwtAccessTokenService implements AccessTokenService {
    private final JwtEncoder encoder;
    private final JwtConfiguration.Settings settings;
    public JwtAccessTokenService(JwtEncoder encoder, JwtConfiguration.Settings settings) {
        this.encoder=encoder; this.settings=settings;
    }
    @Override public IssuedAccessToken issue(AuthenticatedIdentity identity) {
        Instant now=Instant.now();
        var claims=JwtClaimsSet.builder().issuer(settings.issuer()).subject(identity.userId().toString())
                .issuedAt(now).expiresAt(now.plus(settings.ttl())).claim("businessId",identity.businessId())
                .claim("role",identity.role()).build();
        var token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),claims));
        return new IssuedAccessToken(token.getTokenValue(),"Bearer",settings.ttl().toSeconds());
    }
}
