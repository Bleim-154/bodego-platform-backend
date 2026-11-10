package com.bodego.platform.operations.interfaces.rest.transform;

import com.bodego.platform.operations.domain.model.commands.CreateInventoryMovementCommand;
import com.bodego.platform.operations.interfaces.rest.resources.CreateInventoryMovementResource;

public class CreateInventoryMovementCommandFromResourceAssembler {

    public static CreateInventoryMovementCommand toCommandFromResource(
            CreateInventoryMovementResource resource) {
        return new CreateInventoryMovementCommand(
                resource.businessId(),
                resource.lotId(),
                resource.type(),
                resource.quantity(),
                resource.reason()
        );
    }
}
