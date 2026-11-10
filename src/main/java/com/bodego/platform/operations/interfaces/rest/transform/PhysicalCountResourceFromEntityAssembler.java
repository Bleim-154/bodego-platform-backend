package com.bodego.platform.operations.interfaces.rest.transform;

import com.bodego.platform.operations.domain.model.aggregates.PhysicalCount;
import com.bodego.platform.operations.interfaces.rest.resources.PhysicalCountResource;

public class PhysicalCountResourceFromEntityAssembler {

    public static PhysicalCountResource toResourceFromEntity(
            PhysicalCount entity) {

        return new PhysicalCountResource(
                entity.getId(),
                entity.getBusinessId(),
                entity.getLotId(),
                entity.getSystemQuantity(),
                entity.getCountedQuantity(),
                entity.getDifference(),
                entity.getObservation(),
                entity.getCountedAt(),
                entity.getCreatedAt()
        );
    }
}
