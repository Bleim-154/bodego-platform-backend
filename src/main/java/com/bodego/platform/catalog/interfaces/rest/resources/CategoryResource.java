
package com.bodego.platform.catalog.interfaces.rest.resources;

public record CategoryResource(
        Long id,
        Long businessId,
        String name,
        String description
) {}
