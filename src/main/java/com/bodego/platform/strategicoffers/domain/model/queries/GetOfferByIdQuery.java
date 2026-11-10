package com.bodego.platform.strategicoffers.domain.model.queries;

import com.bodego.platform.strategicoffers.domain.model.valueobjects.StrategicOffersValidation;

public record GetOfferByIdQuery(
        Long offerId
) {
    public GetOfferByIdQuery {
        StrategicOffersValidation.positive(offerId);
    }
}
