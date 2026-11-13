package com.bodego.platform.iam.domain.exceptions;

public class ManagedUserNotFoundException extends RuntimeException {
    public ManagedUserNotFoundException() { super("User not found"); }
}
