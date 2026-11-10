package com.bodego.platform.catalog.interfaces.acl.resources;

public record ProductInventorySnapshot(
        Long id,
        Long businessId,
        Integer minimumStock
) {
}
