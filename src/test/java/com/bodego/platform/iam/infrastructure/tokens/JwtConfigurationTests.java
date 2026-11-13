package com.bodego.platform.iam.infrastructure.tokens;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class JwtConfigurationTests {
    final JwtConfiguration config=new JwtConfiguration();
    String key() { return Base64.getEncoder().encodeToString(java.security.SecureRandom.getSeed(32)); }
    @Test void missingWeakOrMalformedSecretsFailClosed() {
        for(String secret:new String[]{"","not_base64!",Base64.getEncoder().encodeToString(new byte[16])}) {
            var error=assertThrows(IllegalStateException.class,() -> config.jwtSettings(secret,"bodego-api",Duration.ofMinutes(15)));
            assertFalse(error.getMessage().contains("not_base64!"));
        }
    }
    @Test void issuerAndLifetimeAreMandatoryAndBounded() {
        for(Duration duration:new Duration[]{Duration.ZERO,Duration.ofSeconds(-1),Duration.ofHours(1)})
            assertThrows(IllegalStateException.class,() -> config.jwtSettings(key(),"bodego-api",duration));
        assertThrows(IllegalStateException.class,() -> config.jwtSettings(key()," ",Duration.ofMinutes(15)));
    }
    @Test void externalKeyIsUsedWithoutGeneratingANewOne() {
        String secret=key();
        var settings=config.jwtSettings(secret,"bodego-api",Duration.ofMinutes(15));
        assertArrayEquals(Base64.getDecoder().decode(secret),settings.key().getEncoded());
        assertEquals("JwtSettings[REDACTED]",settings.toString());
    }
}
