package com.bodego.platform.catalog.interfaces.rest.transform;

import com.bodego.platform.catalog.domain.model.commands.UpdateProductCommand;
import com.bodego.platform.catalog.interfaces.rest.resources.UpdateProductResource;

public class UpdateProductCommandFromResourceAssembler {

    public static UpdateProductCommand toCommandFromResource(
            Long productId,
            UpdateProductResource resource
    ) {
        return new UpdateProductCommand(
                productId,
                resource.categoryId(),
                resource.name(),
                resource.description(),
                resource.price(),
                resource.minimumStock()
        );
    }
}
