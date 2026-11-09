package com.bodego.platform.catalog.interfaces.rest.transform;

import com.bodego.platform.catalog.domain.model.commands.UpdateCategoryCommand;
import com.bodego.platform.catalog.interfaces.rest.resources.UpdateCategoryResource;

public class UpdateCategoryCommandFromResourceAssembler {

    public static UpdateCategoryCommand toCommandFromResource(
            Long categoryId,
            UpdateCategoryResource resource
    ) {
        return new UpdateCategoryCommand(
                categoryId,
                resource.name(),
                resource.description()
        );
    }
}
