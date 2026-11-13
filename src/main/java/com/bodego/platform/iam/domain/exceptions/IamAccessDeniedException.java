package com.bodego.platform.iam.domain.exceptions;

public class IamAccessDeniedException extends RuntimeException {
    public IamAccessDeniedException() { super("Forbidden"); }
}
