package com.bodego.platform.iam.domain.model.valueobjects;

import com.bodego.platform.iam.domain.exceptions.InvalidPasswordException;
import jakarta.persistence.Embeddable;

import java.util.regex.Pattern;

/** Only versioned BCrypt hashes are admissible. Never represents a raw password. */
@Embeddable
public record PasswordHash(String value) {
    private static final Pattern BCRYPT = Pattern.compile(
            "\\{bcrypt}\\$2[aby]\\$(0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}");

    public PasswordHash {
        if (value == null || !BCRYPT.matcher(value).matches()) {
            throw new InvalidPasswordException("A versioned BCrypt hash is required");
        }
    }

    @Override
    public String toString() {
        return "PasswordHash[REDACTED]";
    }
}
