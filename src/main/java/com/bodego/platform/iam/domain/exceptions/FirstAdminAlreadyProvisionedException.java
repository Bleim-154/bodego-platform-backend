package com.bodego.platform.iam.domain.exceptions;

public class FirstAdminAlreadyProvisionedException extends IllegalStateException {
    public FirstAdminAlreadyProvisionedException() {
        super("An administrator has already been provisioned for this business");
    }

    public FirstAdminAlreadyProvisionedException(String message) {
        super(message);
    }
}
