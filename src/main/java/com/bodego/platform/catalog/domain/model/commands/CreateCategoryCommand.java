package com.bodego.platform.catalog.domain.model.commands;

public record CreateCategoryCommand(
        Long businessId,
        String name,
        String description
) {}
