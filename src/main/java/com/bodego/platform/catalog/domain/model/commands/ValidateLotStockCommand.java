package com.bodego.platform.catalog.domain.model.commands;

public record ValidateLotStockCommand(
        Long businessId,
        Long lotId,
        Integer expectedQuantity
) {
}
