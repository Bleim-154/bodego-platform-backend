package com.bodego.platform.strategicoffers.domain.model.commands;

import com.bodego.platform.strategicoffers.domain.model.valueobjects.StrategicOffersValidation;

public record DismissRecommendationCommand(
        Long recommendationId
) {
    public DismissRecommendationCommand {
        StrategicOffersValidation.positive(recommendationId);
    }
}
