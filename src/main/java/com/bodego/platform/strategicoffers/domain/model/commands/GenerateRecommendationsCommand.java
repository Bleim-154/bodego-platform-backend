package com.bodego.platform.strategicoffers.domain.model.commands;

import com.bodego.platform.strategicoffers.domain.model.valueobjects.StrategicOffersValidation;

public record GenerateRecommendationsCommand(
        Long businessId
) {
    public GenerateRecommendationsCommand {
        StrategicOffersValidation.positive(businessId);
    }
}
