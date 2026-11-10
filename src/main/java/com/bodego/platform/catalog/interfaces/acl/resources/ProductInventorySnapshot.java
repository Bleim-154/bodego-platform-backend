package com.bodego.platform.catalog.interfaces.acl.resources;

public record ProductInventorySnapshot(
        Long id,
        Long businessId,
        Integer minimumStock,
        String name,
        Boolean active
) {
    /**
     * Compatible constructor for existing inventory consumers.
     */
    public ProductInventorySnapshot(
            Long id,
            Long businessId,
            Integer minimumStock
    ) {
        this(id, businessId, minimumStock, null, null);
    }
}
