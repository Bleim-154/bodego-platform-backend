package com.bodego.platform.operations.domain.model.commands;

import com.bodego.platform.operations.domain.model.valueobjects.CorrectionRequestStatus;

public record ReviewCorrectionRequestCommand(
        Long businessId,
        Long correctionRequestId,
        CorrectionRequestStatus decision,
        String reviewComment
) {
}
