package com.bodego.platform.iam.domain.model.valueobjects;

import com.bodego.platform.iam.domain.exceptions.InvalidIamDataException;

public final class IamValidation {
    private IamValidation() {}

    public static Long positiveId(Long id) {
        if (id == null || id <= 0) throw new InvalidIamDataException("ID must be positive");
        return id;
    }

    public static String requiredText(String value, int maximum, String field) {
        if (value == null || value.isBlank() || value.strip().length() > maximum) {
            throw new InvalidIamDataException("Invalid " + field);
        }
        return value.strip();
    }
}
