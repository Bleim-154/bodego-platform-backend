package com.bodego.platform.inventory.interfaces.acl.exceptions;

/** Published ACL failure; consumers need not depend on Inventory domain exceptions. */
public final class LotStockValidationConflictException extends RuntimeException {
    public LotStockValidationConflictException(Throwable cause) {
        super("Lot stock does not match the physical count", cause);
    }
}
