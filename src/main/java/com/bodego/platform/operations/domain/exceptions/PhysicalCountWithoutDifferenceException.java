package com.bodego.platform.operations.domain.exceptions;

public final class PhysicalCountWithoutDifferenceException extends StockControlConflictException {
    public PhysicalCountWithoutDifferenceException() { super("Physical count has no stock difference"); }
}
