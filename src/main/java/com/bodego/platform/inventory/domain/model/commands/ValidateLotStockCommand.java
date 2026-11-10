package com.bodego.platform.inventory.domain.model.commands;

public record ValidateLotStockCommand(
        Long businessId,
        Long lotId,
        Integer expectedQuantity
) {
}
