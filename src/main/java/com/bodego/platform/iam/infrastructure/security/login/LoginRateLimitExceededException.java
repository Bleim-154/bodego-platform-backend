package com.bodego.platform.iam.infrastructure.security.login;

public final class LoginRateLimitExceededException extends RuntimeException {
    private final long retryAfter;
    public LoginRateLimitExceededException(long retryAfter) {
        super("Too many requests");
        this.retryAfter=retryAfter;
    }
    public long retryAfter() { return retryAfter; }
}
