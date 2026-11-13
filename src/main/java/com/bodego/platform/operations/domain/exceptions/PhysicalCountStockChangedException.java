package com.bodego.platform.operations.domain.exceptions;

public final class PhysicalCountStockChangedException extends StockControlConflictException {
    public PhysicalCountStockChangedException(Throwable cause) {
        super("Lot stock has changed since the physical count", cause);
    }
}
