package com.bodego.platform.operations.domain.model.queries;

import com.bodego.platform.operations.domain.model.valueobjects.CorrectionRequestStatus;

public record GetCorrectionRequestsByBusinessAndStatusQuery(
        Long businessId,
        CorrectionRequestStatus status
) {
}
