package com.bodego.platform.strategicoffers.domain.model.commands;

import com.bodego.platform.strategicoffers.domain.model.valueobjects.StrategicOffersValidation;

public record DeactivateOfferCommand(
        Long offerId
) {
    public DeactivateOfferCommand {
        StrategicOffersValidation.positive(offerId);
    }
}
