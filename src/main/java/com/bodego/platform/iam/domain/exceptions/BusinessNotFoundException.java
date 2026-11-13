package com.bodego.platform.iam.domain.exceptions;

public class BusinessNotFoundException extends IllegalArgumentException {
    public BusinessNotFoundException() {
        super("Business not found");
    }

    public BusinessNotFoundException(String message) {
        super(message);
    }
}
