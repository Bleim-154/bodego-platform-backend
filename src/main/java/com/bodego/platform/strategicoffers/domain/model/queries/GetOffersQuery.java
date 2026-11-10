package com.bodego.platform.strategicoffers.domain.model.queries;

import com.bodego.platform.strategicoffers.domain.model.valueobjects.StrategicOffersValidation;

public record GetOffersQuery(
        Long businessId,
        Long productId,
        Boolean active
) {
    public GetOffersQuery {
        StrategicOffersValidation.optionalPositive(businessId);
        StrategicOffersValidation.optionalPositive(productId);
    }
}
