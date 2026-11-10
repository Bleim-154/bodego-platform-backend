package com.bodego.platform.monitoring.interfaces.rest.transform;

import com.bodego.platform.monitoring.domain.model.commands.GenerateAlertsCommand;
import com.bodego.platform.monitoring.interfaces.rest.resources.GenerateAlertsResource;

public class GenerateAlertsCommandFromResourceAssembler {

    public static GenerateAlertsCommand toCommandFromResource(GenerateAlertsResource resource) {
        return new GenerateAlertsCommand(resource.businessId());
    }
}
