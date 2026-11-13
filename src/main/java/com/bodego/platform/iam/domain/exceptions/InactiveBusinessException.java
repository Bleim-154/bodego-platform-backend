package com.bodego.platform.iam.domain.exceptions;

public class InactiveBusinessException extends IllegalStateException {
    public InactiveBusinessException() {
        super("Business is inactive");
    }

    public InactiveBusinessException(String message) {
        super(message);
    }
}
