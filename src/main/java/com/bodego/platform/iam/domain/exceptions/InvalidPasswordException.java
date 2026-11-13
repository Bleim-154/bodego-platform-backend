package com.bodego.platform.iam.domain.exceptions;

public class InvalidPasswordException extends InvalidIamDataException {
    public InvalidPasswordException() {
        super("Invalid password");
    }

    public InvalidPasswordException(String message) {
        super(message);
    }
}
