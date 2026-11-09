package com.bodego.platform.catalog.interfaces.rest.transform;

import com.bodego.platform.catalog.domain.model.commands.CreateCategoryCommand;
import com.bodego.platform.catalog.interfaces.rest.resources.CreateCategoryResource;

public class CreateCategoryCommandFromResourceAssembler {

    public static CreateCategoryCommand toCommandFromResource(
            CreateCategoryResource resource
    ) {
        return new CreateCategoryCommand(
                resource.businessId(),
                resource.name(),
                resource.description()
        );
    }
}
