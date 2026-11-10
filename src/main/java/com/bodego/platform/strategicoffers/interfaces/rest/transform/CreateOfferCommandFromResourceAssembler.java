package com.bodego.platform.strategicoffers.interfaces.rest.transform;

import com.bodego.platform.strategicoffers.domain.model.commands.CreateOfferCommand;
import com.bodego.platform.strategicoffers.interfaces.rest.resources.CreateOfferResource;

public class CreateOfferCommandFromResourceAssembler {

    public static CreateOfferCommand toCommandFromResource(
            CreateOfferResource resource
    ) {
        return new CreateOfferCommand(
                resource.businessId(),
                resource.productId(),
                resource.title(),
                resource.discountPercentage(),
                resource.startDate(),
                resource.endDate()
        );
    }
}
