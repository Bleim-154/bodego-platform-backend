package com.bodego.platform.strategicoffers.domain.model.commands;

import com.bodego.platform.strategicoffers.domain.model.valueobjects.StrategicOffersValidation;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateOfferCommand(
        Long offerId,
        String title,
        BigDecimal discountPercentage,
        LocalDate startDate,
        LocalDate endDate
) {
    public UpdateOfferCommand {
        StrategicOffersValidation.positive(offerId);
    }
}
