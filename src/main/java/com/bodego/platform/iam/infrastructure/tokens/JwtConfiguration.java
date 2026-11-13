package com.bodego.platform.iam.infrastructure.tokens;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.*;
import java.util.Base64;

@Configuration
public class JwtConfiguration {
    public record Settings(SecretKey key, String issuer, Duration ttl) {
        @Override public String toString() { return "JwtSettings[REDACTED]"; }
    }

    @Bean Settings jwtSettings(@Value("${bodego.security.jwt.secret-base64:}") String secret,
                              @Value("${bodego.security.jwt.issuer:bodego-api}") String issuer,
                              @Value("${bodego.security.jwt.ttl:PT15M}") Duration ttl) {
        byte[] bytes;
        try { bytes=Base64.getDecoder().decode(secret); }
        catch(IllegalArgumentException ex) { throw new IllegalStateException("JWT secret must be valid Base64 with at least 32 random decoded bytes"); }
        if(bytes.length < 32 || issuer.isBlank() || ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalStateException("Secure JWT configuration required: external key >=256 bits, issuer, TTL >0 and <=15 minutes");
        }
        var key=new SecretKeySpec(bytes,"HmacSHA256");
        java.util.Arrays.fill(bytes,(byte)0);
        return new Settings(key,issuer,ttl);
    }

    @Bean JwtEncoder jwtEncoder(Settings settings) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(settings.key()));
    }

    @Bean JwtDecoder jwtDecoder(Settings settings) {
        var decoder=NimbusJwtDecoder.withSecretKey(settings.key()).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(settings.issuer()), new JwtTimestampValidator(Duration.ZERO), jwt -> {
            var now=Instant.now();
            var iat=jwt.getIssuedAt(); var exp=jwt.getExpiresAt();
            boolean valid=iat!=null && exp!=null && !iat.isAfter(now) && exp.isAfter(iat)
                    && Duration.between(iat,exp).compareTo(settings.ttl())<=0
                    && jwt.getSubject()!=null && jwt.getSubject().matches("[1-9][0-9]*")
                    && jwt.getClaims().get("businessId") instanceof Number
                    && jwt.getClaimAsString("role")!=null
                    && java.util.Set.of("ADMIN","EMPLOYEE").contains(jwt.getClaimAsString("role"));
            try {
                valid=valid && Long.parseLong(jwt.getSubject())>0
                        && Long.parseLong(jwt.getClaims().get("businessId").toString())>0;
            } catch(RuntimeException exception) { valid=false; }
            return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token","Invalid token",null));
        }));
        return decoder;
    }
}
