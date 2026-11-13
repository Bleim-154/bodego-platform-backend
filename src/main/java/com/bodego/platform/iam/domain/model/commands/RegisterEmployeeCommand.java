package com.bodego.platform.iam.domain.model.commands;

import java.util.Arrays;

/** Untrusted registration input has no business identifier or role. */
public record RegisterEmployeeCommand(String fullName, String email, char[] password) implements AutoCloseable {
    public RegisterEmployeeCommand { password=password==null?new char[0]:password.clone(); }
    @Override public char[] password() { return password.clone(); }
    @Override public void close() { Arrays.fill(password,'\0'); }
    @Override public String toString() { return "RegisterEmployeeCommand[REDACTED]"; }
}
