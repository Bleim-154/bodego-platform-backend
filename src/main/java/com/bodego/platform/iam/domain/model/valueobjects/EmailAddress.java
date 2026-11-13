package com.bodego.platform.iam.domain.model.valueobjects;

import com.bodego.platform.iam.domain.exceptions.InvalidEmailException;
import jakarta.persistence.Embeddable;

import java.util.Locale;
import java.util.regex.Pattern;

/** Product policy: ASCII email, globally case-insensitive, stored in canonical form. */
@Embeddable
public record EmailAddress(String value) {
    private static final Pattern EMAIL = Pattern.compile(
            "[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
            + "@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+");

    public EmailAddress {
        if (value == null) throw new InvalidEmailException();
        value = value.strip().toLowerCase(Locale.ROOT);
        int at = value.indexOf('@');
        if (value.length() > 254 || at < 1 || at > 64 || !EMAIL.matcher(value).matches()) {
            throw new InvalidEmailException();
        }
        for (String label : value.substring(at + 1).split("\\.")) {
            if (label.length() > 63) throw new InvalidEmailException();
        }
    }
}
