package com.bodego.platform.catalog.interfaces.rest.transform;

import com.bodego.platform.catalog.domain.model.aggregates.Product;
import com.bodego.platform.catalog.interfaces.rest.resources.ProductResource;

public class ProductResourceFromEntityAssembler {

    public static ProductResource toResourceFromEntity(Product entity) {
        return new ProductResource(
                entity.getId(),
                entity.getBusinessId(),
                entity.getCategoryId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPrice(),
                entity.getMinimumStock(),
                entity.getActive()
        );
    }
}
