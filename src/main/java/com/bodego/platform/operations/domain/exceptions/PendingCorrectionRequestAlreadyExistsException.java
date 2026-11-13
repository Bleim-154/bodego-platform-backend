package com.bodego.platform.operations.domain.exceptions;

public final class PendingCorrectionRequestAlreadyExistsException extends StockControlConflictException {
    public PendingCorrectionRequestAlreadyExistsException() { super("A pending correction request already exists"); }
}
