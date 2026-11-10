package com.bodego.platform.operations.interfaces.rest.transform;

import com.bodego.platform.operations.domain.model.aggregates.Waste;
import com.bodego.platform.operations.interfaces.rest.resources.WasteResource;

public class WasteResourceFromEntityAssembler {

    public static WasteResource toResourceFromEntity(Waste entity) {

        return new WasteResource(
                entity.getId(),
                entity.getBusinessId(),
                entity.getLotId(),
                entity.getQuantity(),
                entity.getReason(),
                entity.getObservation(),
                entity.getRegisteredAt(),
                entity.getCreatedAt()
        );
    }
}
