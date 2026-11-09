package com.bodego.platform.catalog.domain.model.commands;

import java.math.BigDecimal;

public record CreateProductCommand(
        Long businessId,
        Long categoryId,
        String name,
        String description,
        BigDecimal price,
        Integer minimumStock
) {
}
