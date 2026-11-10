package com.bodego.platform.strategicoffers.interfaces.rest.transform;

import com.bodego.platform.strategicoffers.domain.model.commands.UpdateOfferCommand;
import com.bodego.platform.strategicoffers.interfaces.rest.resources.UpdateOfferResource;

public class UpdateOfferCommandFromResourceAssembler {

    public static UpdateOfferCommand toCommandFromResource(
            Long id,
            UpdateOfferResource resource
    ) {
        return new UpdateOfferCommand(
                id,
                resource.title(),
                resource.discountPercentage(),
                resource.startDate(),
                resource.endDate()
        );
    }
}
