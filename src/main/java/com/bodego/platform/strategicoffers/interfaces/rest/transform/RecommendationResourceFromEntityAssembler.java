package com.bodego.platform.strategicoffers.interfaces.rest.transform;

import com.bodego.platform.strategicoffers.domain.model.aggregates.Recommendation;
import com.bodego.platform.strategicoffers.interfaces.rest.resources.RecommendationResource;

public class RecommendationResourceFromEntityAssembler {

    public static RecommendationResource toResourceFromEntity(
            Recommendation entity
    ) {
        return new RecommendationResource(
                entity.getId(),
                entity.getBusinessId(),
                entity.getProductId(),
                entity.getLotId(),
                entity.getType(),
                entity.getMessage(),
                entity.getGeneratedAt(),
                entity.isDismissed(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
