package com.bodego.platform.inventory.interfaces.rest.transform;

import com.bodego.platform.inventory.domain.model.aggregates.Lot;
import com.bodego.platform.inventory.interfaces.rest.resources.LotResource;

public class LotResourceFromEntityAssembler {

    public static LotResource toResourceFromEntity(Lot entity) {
        return new LotResource(
                entity.getId(),
                entity.getBusinessId(),
                entity.getProductId(),
                entity.getBatchNumber(),
                entity.getQuantity(),
                entity.getExpirationDate()
        );
    }
}
