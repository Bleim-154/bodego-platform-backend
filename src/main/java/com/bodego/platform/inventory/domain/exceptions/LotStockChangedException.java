package com.bodego.platform.inventory.domain.exceptions;

/** The locked lot no longer matches the stock captured by a physical count. */
public final class LotStockChangedException extends RuntimeException {
    public LotStockChangedException() { super("Lot stock has changed since the physical count"); }
}
