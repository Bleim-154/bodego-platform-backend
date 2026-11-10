package com.bodego.platform.strategicoffers.domain.model.queries;

import com.bodego.platform.strategicoffers.domain.model.valueobjects.StrategicOffersValidation;

public record GetRecommendationByIdQuery(
        Long recommendationId
) {
    public GetRecommendationByIdQuery {
        StrategicOffersValidation.positive(recommendationId);
    }
}
