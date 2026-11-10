package com.bodego.platform.operations.interfaces.rest.transform;

import com.bodego.platform.operations.domain.model.commands.CreatePhysicalCountCommand;
import com.bodego.platform.operations.interfaces.rest.resources.CreatePhysicalCountResource;

public class CreatePhysicalCountCommandFromResourceAssembler {

    public static CreatePhysicalCountCommand toCommandFromResource(CreatePhysicalCountResource resource) {
        return new CreatePhysicalCountCommand(resource.lotId(), resource.userId(), resource.physicalQuantity(), resource.date(), resource.observation());
    }
}
