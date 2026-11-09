package com.bodego.platform.catalog.domain.model.commands;

import java.math.BigDecimal;

public record UpdateProductCommand(
        Long productId,
        Long categoryId,
        String name,
        String description,
        BigDecimal price,
        Integer minimumStock
) {
}
