package com.bodego.platform.catalog.interfaces.rest.transform;

import com.bodego.platform.catalog.domain.model.commands.CreateProductCommand;
import com.bodego.platform.catalog.interfaces.rest.resources.CreateProductResource;

public class CreateProductCommandFromResourceAssembler {

    public static CreateProductCommand toCommandFromResource(
            CreateProductResource resource
    ) {
        return new CreateProductCommand(
                resource.businessId(),
                resource.categoryId(),
                resource.name(),
                resource.description(),
                resource.price(),
                resource.minimumStock()
        );
    }
}
