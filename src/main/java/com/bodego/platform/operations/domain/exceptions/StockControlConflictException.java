package com.bodego.platform.operations.domain.exceptions;

/** Expected conflict with the current stock-control state, never an infrastructure failure. */
public abstract class StockControlConflictException extends RuntimeException {
    protected StockControlConflictException(String message) { super(message); }
    protected StockControlConflictException(String message, Throwable cause) { super(message, cause); }
}
