package com.bodego.platform.strategicoffers.interfaces.rest.transform;

import com.bodego.platform.strategicoffers.domain.model.commands.GenerateRecommendationsCommand;
import com.bodego.platform.strategicoffers.interfaces.rest.resources.GenerateRecommendationsResource;

public class GenerateRecommendationsCommandFromResourceAssembler {

    public static GenerateRecommendationsCommand toCommandFromResource(
            GenerateRecommendationsResource resource
    ) {
        return new GenerateRecommendationsCommand(resource.businessId());
    }
}
