package com.bodego.platform.shared.application.security;

/** The same result for missing and foreign resources. Never include foreign IDs or tenant details. */
public class BusinessResourceNotFoundException extends RuntimeException {
    public BusinessResourceNotFoundException() { super("Resource not found"); }
}
