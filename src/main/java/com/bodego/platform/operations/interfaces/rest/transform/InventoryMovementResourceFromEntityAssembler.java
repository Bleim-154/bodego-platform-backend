package com.bodego.platform.operations.interfaces.rest.transform;

import com.bodego.platform.operations.domain.model.aggregates.InventoryMovement;
import com.bodego.platform.operations.interfaces.rest.resources.InventoryMovementResource;

public class InventoryMovementResourceFromEntityAssembler {

    public static InventoryMovementResource toResourceFromEntity(
            InventoryMovement entity) {
        return new InventoryMovementResource(
                entity.getId(),
                entity.getBusinessId(),
                entity.getLotId(),
                entity.getType(),
                entity.getQuantity(),
                entity.getReason(),
                entity.getCreatedAt()
        );
    }
}
