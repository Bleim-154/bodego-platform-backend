package com.bodego.platform.monitoring.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record GenerateAlertsResource(
        @NotNull @Positive Long businessId
) {
}
