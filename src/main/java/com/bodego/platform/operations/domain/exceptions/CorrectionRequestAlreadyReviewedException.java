package com.bodego.platform.operations.domain.exceptions;

public final class CorrectionRequestAlreadyReviewedException extends StockControlConflictException {
    public CorrectionRequestAlreadyReviewedException() { super("Correction request has already been reviewed"); }
}
