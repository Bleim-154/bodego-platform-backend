package com.bodego.platform.operations.interfaces.rest.transform;

import com.bodego.platform.operations.domain.model.commands.RegisterWasteCommand;
import com.bodego.platform.operations.interfaces.rest.resources.RegisterWasteResource;

public class RegisterWasteCommandFromResourceAssembler {

    public static RegisterWasteCommand toCommandFromResource(
            RegisterWasteResource resource) {

        return new RegisterWasteCommand(
                resource.businessId(),
                resource.lotId(),
                resource.quantity(),
                resource.reason(),
                resource.observation()
        );
    }
}
