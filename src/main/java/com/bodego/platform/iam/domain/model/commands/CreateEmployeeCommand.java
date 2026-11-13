package com.bodego.platform.iam.domain.model.commands;

import com.bodego.platform.iam.domain.exceptions.InvalidPasswordException;
import java.util.Arrays;

/** Internal, short-lived input; consumed and erased by the command service. No HTTP binding. */
public record CreateEmployeeCommand(Long businessId, String fullName, String email, char[] password)
        implements AutoCloseable {
    public CreateEmployeeCommand {
        if (password == null) throw new InvalidPasswordException();
        password = password.clone();
    }

    @Override
    public char[] password() { return password.clone(); }

    @Override
    public void close() { Arrays.fill(password, '\0'); }

    @Override
    public String toString() { return "CreateEmployeeCommand[businessId=" + businessId + ", password=REDACTED]"; }
}
