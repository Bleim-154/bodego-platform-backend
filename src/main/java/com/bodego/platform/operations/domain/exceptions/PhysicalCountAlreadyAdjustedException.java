package com.bodego.platform.operations.domain.exceptions;

public final class PhysicalCountAlreadyAdjustedException extends StockControlConflictException {
    public PhysicalCountAlreadyAdjustedException() { super("Physical count has already been adjusted"); }
}
