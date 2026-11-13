package com.bodego.platform.shared.application.security;

public class BusinessAccessDeniedException extends RuntimeException {
    public BusinessAccessDeniedException() { super("Forbidden"); }
}
