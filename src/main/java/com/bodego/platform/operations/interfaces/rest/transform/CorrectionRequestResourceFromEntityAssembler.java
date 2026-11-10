package com.bodego.platform.operations.interfaces.rest.transform;

import com.bodego.platform.operations.domain.model.aggregates.CorrectionRequest;
import com.bodego.platform.operations.interfaces.rest.resources.CorrectionRequestResource;

public class CorrectionRequestResourceFromEntityAssembler {

    public static CorrectionRequestResource toResourceFromEntity(
            CorrectionRequest entity
    ) {
        return new CorrectionRequestResource(
                entity.getId(),
                entity.getBusinessId(),
                entity.getPhysicalCountId(),
                entity.getReason(),
                entity.getStatus(),
                entity.getRequestedAt(),
                entity.getReviewedAt(),
                entity.getReviewComment(),
                entity.getCreatedAt()
        );
    }
}
