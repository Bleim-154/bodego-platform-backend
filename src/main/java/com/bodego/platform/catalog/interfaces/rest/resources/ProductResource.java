package com.bodego.platform.catalog.interfaces.rest.resources;

import java.math.BigDecimal;

public record ProductResource(
        Long id,
        Long businessId,
        Long categoryId,
        String name,
        String description,
        BigDecimal price,
        Integer minimumStock,
        Boolean active
) {
}
