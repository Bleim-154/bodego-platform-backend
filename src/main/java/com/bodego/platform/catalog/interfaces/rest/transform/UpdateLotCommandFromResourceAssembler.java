package com.bodego.platform.catalog.interfaces.rest.transform;

import com.bodego.platform.catalog.domain.model.commands.UpdateLotCommand;
import com.bodego.platform.catalog.interfaces.rest.resources.UpdateLotResource;

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
