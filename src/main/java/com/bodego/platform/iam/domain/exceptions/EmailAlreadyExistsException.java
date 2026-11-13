package com.bodego.platform.iam.domain.exceptions;

// Do not inherit IllegalStateException: JPA exception translation treats it as API misuse.
public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException() {
        super("User email already exists");
    }

    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
