package com.bodego.platform.catalog.interfaces.rest.transform;

import com.bodego.platform.catalog.domain.model.aggregates.Category;
import com.bodego.platform.catalog.interfaces.rest.resources.CategoryResource;

public class CategoryResourceFromEntityAssembler {

    public static CategoryResource toResourceFromEntity(Category entity) {
        return new CategoryResource(
                entity.getId(),
                entity.getBusinessId(),
                entity.getName(),
                entity.getDescription()
        );
    }
}
