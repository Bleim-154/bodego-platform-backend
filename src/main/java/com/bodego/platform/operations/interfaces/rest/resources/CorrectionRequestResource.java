package com.bodego.platform.operations.interfaces.rest.resources;

import com.bodego.platform.operations.domain.model.valueobjects.CorrectionRequestStatus;
import java.time.Instant;

public record CorrectionRequestResource(
        Long id,
        Long businessId,
        Long physicalCountId,
        String reason,
        CorrectionRequestStatus status,
        Instant requestedAt,
        Instant reviewedAt,
        String reviewComment,
        Instant createdAt
) {
}
