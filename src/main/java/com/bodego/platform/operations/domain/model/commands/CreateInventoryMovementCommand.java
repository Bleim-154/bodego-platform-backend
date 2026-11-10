package com.bodego.platform.operations.domain.model.commands;

import com.bodego.platform.operations.domain.model.valueobjects.MovementType;

public record CreateInventoryMovementCommand(
        Long businessId,
        Long lotId,
        MovementType type,
        Integer quantity,
        String reason
) {
}
