package com.bodego.platform.catalog.domain.model.commands;

public record UpdateCategoryCommand(
        Long categoryId,
        String name,
        String description
) {}
