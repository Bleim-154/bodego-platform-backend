package com.bodego.platform.operations.interfaces.rest.transform;

import com.bodego.platform.operations.domain.model.commands.ApplyStockAdjustmentCommand;
import com.bodego.platform.operations.interfaces.rest.resources.ApplyStockAdjustmentResource;

public class ApplyStockAdjustmentCommandFromResourceAssembler {

    public static ApplyStockAdjustmentCommand toCommandFromResource(
            ApplyStockAdjustmentResource resource) {

        return new ApplyStockAdjustmentCommand(
                resource.businessId(),
                resource.physicalCountId(),
                resource.reason()
        );
    }
}
