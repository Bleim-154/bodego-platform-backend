package com.bodego.platform.strategicoffers.interfaces.rest.resources;

import jakarta.validation.constraints.*;

public record GenerateRecommendationsResource(
        @NotNull @Positive Long businessId
) {
}
