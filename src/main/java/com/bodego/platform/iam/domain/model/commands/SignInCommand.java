package com.bodego.platform.iam.domain.model.commands;

import java.util.Arrays;

public record SignInCommand(String email, char[] password) implements AutoCloseable {
    public SignInCommand { password = password == null ? new char[0] : password.clone(); }
    @Override public char[] password() { return password.clone(); }
    @Override public void close() { Arrays.fill(password, '\0'); }
    @Override public String toString() { return "SignInCommand[REDACTED]"; }
}
