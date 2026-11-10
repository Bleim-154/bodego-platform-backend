package com.bodego.platform.operations.interfaces.rest.transform;

import com.bodego.platform.operations.domain.model.commands.ReviewCorrectionRequestCommand;
import com.bodego.platform.operations.interfaces.rest.resources.ReviewCorrectionRequestResource;

public class ReviewCorrectionRequestCommandFromResourceAssembler {

    public static ReviewCorrectionRequestCommand toCommandFromResource(
            Long correctionRequestId,
            ReviewCorrectionRequestResource resource
    ) {
        return new ReviewCorrectionRequestCommand(
                resource.businessId(),
                correctionRequestId,
                resource.decision(),
                resource.reviewComment()
        );
    }
}
