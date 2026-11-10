package com.bodego.platform.operations.interfaces.rest.resources;

import java.time.Instant;

public record StockAdjustmentResource(
        Long id,
        Long businessId,
        Long physicalCountId,
        Long lotId,
        Integer previousQuantity,
        Integer newQuantity,
        Integer difference,
        String reason,
        Instant adjustedAt,
        Instant createdAt
) {
}
