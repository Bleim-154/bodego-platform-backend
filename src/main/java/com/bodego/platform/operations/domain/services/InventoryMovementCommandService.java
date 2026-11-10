package com.bodego.platform.operations.domain.services;

import com.bodego.platform.operations.domain.model.commands.CreateInventoryMovementCommand;

public interface InventoryMovementCommandService {

    Long handle(CreateInventoryMovementCommand command);
}
