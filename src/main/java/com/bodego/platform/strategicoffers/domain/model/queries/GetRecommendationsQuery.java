package com.bodego.platform.strategicoffers.domain.model.queries;

import com.bodego.platform.strategicoffers.domain.model.valueobjects.RecommendationType;
import com.bodego.platform.strategicoffers.domain.model.valueobjects.StrategicOffersValidation;

public record GetRecommendationsQuery(
        Long businessId,
        Long productId,
        RecommendationType type,
        Boolean dismissed
) {
    public GetRecommendationsQuery {
        StrategicOffersValidation.optionalPositive(businessId);
        StrategicOffersValidation.optionalPositive(productId);
    }
}
