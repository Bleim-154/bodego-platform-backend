package com.bodego.platform.iam.domain.exceptions;

public class InvalidEmailException extends InvalidIamDataException {
    public InvalidEmailException() {
        super("Invalid user email");
    }

    public InvalidEmailException(String message) {
        super(message);
    }
}
