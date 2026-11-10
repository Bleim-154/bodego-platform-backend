package com.bodego.platform.inventory.interfaces.rest.transform;

import com.bodego.platform.inventory.domain.model.commands.UpdateLotCommand;
import com.bodego.platform.inventory.interfaces.rest.resources.UpdateLotResource;

public class UpdateLotCommandFromResourceAssembler {

    public static UpdateLotCommand toCommandFromResource(
            Long lotId,
            UpdateLotResource resource
    ) {
        return new UpdateLotCommand(
                lotId,
                resource.batchNumber(),
                resource.expirationDate()
        );
    }
}
