package com.bodego.platform.strategicoffers.domain.model.commands;

import com.bodego.platform.strategicoffers.domain.model.valueobjects.StrategicOffersValidation;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateOfferCommand(
        Long businessId,
        Long productId,
        String title,
        BigDecimal discountPercentage,
        LocalDate startDate,
        LocalDate endDate
) {
    public CreateOfferCommand {
        StrategicOffersValidation.positive(businessId);
        StrategicOffersValidation.positive(productId);
    }
}
