package com.bodego.platform.iam.infrastructure.hashing;

import com.bodego.platform.iam.application.internal.outboundservices.hashing.PasswordHashingService;
import com.bodego.platform.iam.domain.exceptions.InvalidPasswordException;
import com.bodego.platform.iam.domain.model.valueobjects.PasswordHash;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/** Password hashing adapter shared by internal provisioning and HTTP authentication. */
@Service
public class BCryptPasswordHashingService implements PasswordHashingService {
    private static final int STRENGTH = 12;
    private static final String PREFIX = "{bcrypt}";
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(
            BCryptPasswordEncoder.BCryptVersion.$2B, STRENGTH);

    @Override
    public PasswordHash hash(CharSequence rawPassword) {
        validate(rawPassword);
        return new PasswordHash(PREFIX + encoder.encode(rawPassword));
    }

    @Override
    public boolean matches(CharSequence rawPassword, PasswordHash storedHash) {
        if (rawPassword == null || storedHash == null) return false;
        try {
            validate(rawPassword);
            return encoder.matches(rawPassword, storedHash.value().substring(PREFIX.length()));
        } catch (InvalidPasswordException exception) {
            return false;
        }
    }

    @Override
    public boolean needsRehash(PasswordHash storedHash) {
        if (storedHash == null) throw new InvalidPasswordException();
        return encoder.upgradeEncoding(storedHash.value().substring(PREFIX.length()));
    }

    private void validate(CharSequence rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8 || rawPassword.toString().isBlank()) {
            throw new InvalidPasswordException("Password must contain at least 8 characters");
        }
        // Reject overlength passwords instead of allowing BCrypt's 72-byte truncation.
        if (StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(rawPassword)).remaining() > 72) {
            throw new InvalidPasswordException("Password exceeds BCrypt's 72-byte limit");
        }
    }
}
