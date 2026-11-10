package com.bodego.platform.strategicoffers.domain.model.commands;

import com.bodego.platform.strategicoffers.domain.model.valueobjects.StrategicOffersValidation;

public record ActivateOfferCommand(
        Long offerId
) {
    public ActivateOfferCommand {
        StrategicOffersValidation.positive(offerId);
    }
}
