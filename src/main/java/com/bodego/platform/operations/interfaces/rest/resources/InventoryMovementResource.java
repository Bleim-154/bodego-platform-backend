package com.bodego.platform.operations.interfaces.rest.resources;

import com.bodego.platform.operations.domain.model.valueobjects.MovementType;
import java.time.Instant;

public record InventoryMovementResource(
        Long id,
        Long businessId,
        Long lotId,
        MovementType type,
        Integer quantity,
        String reason,
        Instant createdAt
) {
}
