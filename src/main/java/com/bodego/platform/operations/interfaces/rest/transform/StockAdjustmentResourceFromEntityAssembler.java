package com.bodego.platform.operations.interfaces.rest.transform;

import com.bodego.platform.operations.domain.model.aggregates.StockAdjustment;
import com.bodego.platform.operations.interfaces.rest.resources.StockAdjustmentResource;

public class StockAdjustmentResourceFromEntityAssembler {

    public static StockAdjustmentResource toResourceFromEntity(
            StockAdjustment entity) {

        return new StockAdjustmentResource(
                entity.getId(),
                entity.getBusinessId(),
                entity.getPhysicalCountId(),
                entity.getLotId(),
                entity.getPreviousQuantity(),
                entity.getNewQuantity(),
                entity.getDifference(),
                entity.getReason(),
                entity.getAdjustedAt(),
                entity.getCreatedAt()
        );
    }
}
