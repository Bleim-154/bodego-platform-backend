package com.bodego.platform.iam.domain.exceptions;

public class InvalidIamDataException extends IllegalArgumentException {
    public InvalidIamDataException() {
        super("Invalid IAM data");
    }

    public InvalidIamDataException(String message) {
        super(message);
    }
}
