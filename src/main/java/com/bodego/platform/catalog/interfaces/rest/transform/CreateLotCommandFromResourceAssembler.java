package com.bodego.platform.catalog.interfaces.rest.transform;

import com.bodego.platform.catalog.domain.model.commands.CreateLotCommand;
import com.bodego.platform.catalog.interfaces.rest.resources.CreateLotResource;

public class CreateLotCommandFromResourceAssembler {

    public static CreateLotCommand toCommandFromResource(
            CreateLotResource resource
    ) {
        return new CreateLotCommand(
                resource.businessId(),
                resource.productId(),
                resource.batchNumber(),
                resource.quantity(),
                resource.expirationDate()
        );
    }
}
