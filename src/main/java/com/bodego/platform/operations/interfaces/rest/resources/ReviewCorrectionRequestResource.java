package com.bodego.platform.operations.interfaces.rest.resources;

import com.bodego.platform.operations.domain.model.valueobjects.CorrectionRequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReviewCorrectionRequestResource(
        @NotNull @Positive Long businessId,
        @NotNull CorrectionRequestStatus decision,
        @Size(max = 500) String reviewComment
) {
}
