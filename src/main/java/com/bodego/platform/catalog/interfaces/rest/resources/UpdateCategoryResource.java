package com.bodego.platform.catalog.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCategoryResource(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description
) {
}
