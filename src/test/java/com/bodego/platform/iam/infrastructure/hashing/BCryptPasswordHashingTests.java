package com.bodego.platform.iam.infrastructure.hashing;

import com.bodego.platform.iam.domain.exceptions.InvalidPasswordException;
import com.bodego.platform.iam.domain.model.valueobjects.PasswordHash;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

class BCryptPasswordHashingTests {
    private final BCryptPasswordHashingService hashing = new BCryptPasswordHashingService();

    @Test
    void bcryptUsesRandomSaltAndVerifiesWithoutStoringRawPassword() {
        String input = "  Fixture-password-2026  ";
        var first = hashing.hash(input);
        var second = hashing.hash(input);
        assertNotEquals(first.value(), second.value());
        assertTrue(first.value().startsWith("{bcrypt}$2b$12$"));
        assertTrue(hashing.matches(input, first));
        assertFalse(hashing.matches(input.strip(), first));
        assertFalse(hashing.matches("wrong-password", first));
        assertFalse(hashing.matches(null, first));
        assertFalse(hashing.needsRehash(first));
    }

    @Test
    void rejectsEmptyShortAndUtf8OverlengthInsteadOfTruncating() {
        assertThrows(InvalidPasswordException.class, () -> hashing.hash(null));
        assertThrows(InvalidPasswordException.class, () -> hashing.hash("short"));
        assertThrows(InvalidPasswordException.class, () -> hashing.hash(" ".repeat(8)));
        assertThrows(InvalidPasswordException.class, () -> hashing.hash("é".repeat(37)));
        assertThrows(InvalidPasswordException.class, () -> hashing.hash("x".repeat(73)));
    }

    @Test
    void recognizesOlderBcryptCost() {
        var older = new PasswordHash("{bcrypt}" + new BCryptPasswordEncoder(4).encode("Fixture-password-2026"));
        assertTrue(hashing.matches("Fixture-password-2026", older));
        assertTrue(hashing.needsRehash(older));
    }
}
