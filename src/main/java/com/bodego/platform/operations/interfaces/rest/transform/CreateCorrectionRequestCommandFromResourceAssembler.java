package com.bodego.platform.operations.interfaces.rest.transform;

import com.bodego.platform.operations.domain.model.commands.CreateCorrectionRequestCommand;
import com.bodego.platform.operations.interfaces.rest.resources.CreateCorrectionRequestResource;

public class CreateCorrectionRequestCommandFromResourceAssembler {

    public static CreateCorrectionRequestCommand toCommandFromResource(
            CreateCorrectionRequestResource resource
    ) {
        return new CreateCorrectionRequestCommand(
                resource.businessId(),
                resource.physicalCountId(),
                resource.reason()
        );
    }
}
