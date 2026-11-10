package com.bodego.platform.operations.domain.model.commands;

public record ApplyStockAdjustmentCommand(
        Long businessId,
        Long physicalCountId,
        String reason
) {
}
