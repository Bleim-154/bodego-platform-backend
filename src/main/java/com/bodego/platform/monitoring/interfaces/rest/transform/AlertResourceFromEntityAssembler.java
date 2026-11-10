package com.bodego.platform.monitoring.interfaces.rest.transform;

import com.bodego.platform.monitoring.domain.model.aggregates.Alert;
import com.bodego.platform.monitoring.interfaces.rest.resources.AlertResource;

public class AlertResourceFromEntityAssembler {

    public static AlertResource toResourceFromEntity(Alert entity) {
        return new AlertResource(
                entity.getId(), entity.getBusinessId(),
                entity.getProductId(), entity.getLotId(),
                entity.getType(), entity.getSeverity(), entity.getMessage(),
                entity.getResolved(), entity.getGeneratedAt(),
                entity.getResolvedAt(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
