package com.bodego.platform.monitoring.interfaces.rest.transform;

import com.bodego.platform.monitoring.domain.model.aggregates.AuditLog;
import com.bodego.platform.monitoring.interfaces.rest.resources.AuditLogResource;

public class AuditLogResourceFromEntityAssembler {

    public static AuditLogResource toResourceFromEntity(AuditLog entity) {
        return new AuditLogResource(
                entity.getId(), entity.getBusinessId(), entity.getUserId(),
                entity.getAction(), entity.getEntityType(), entity.getEntityId(),
                entity.getDescription(), entity.getOccurredAt(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
