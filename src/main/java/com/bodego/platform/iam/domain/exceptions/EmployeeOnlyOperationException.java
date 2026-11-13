package com.bodego.platform.iam.domain.exceptions;

public class EmployeeOnlyOperationException extends RuntimeException {
    public EmployeeOnlyOperationException() { super("Only employee status may be changed"); }
}
