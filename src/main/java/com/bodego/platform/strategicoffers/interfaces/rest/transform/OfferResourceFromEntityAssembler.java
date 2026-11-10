package com.bodego.platform.strategicoffers.interfaces.rest.transform;

import com.bodego.platform.strategicoffers.domain.model.aggregates.Offer;
import com.bodego.platform.strategicoffers.interfaces.rest.resources.OfferResource;

public class OfferResourceFromEntityAssembler {

    public static OfferResource toResourceFromEntity(
            Offer entity
    ) {
        return new OfferResource(
                entity.getId(),
                entity.getBusinessId(),
                entity.getProductId(),
                entity.getTitle(),
                entity.getDiscountPercentage(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
