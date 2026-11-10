package com.bodego.platform.operations.domain.services;

import com.bodego.platform.operations.domain.model.commands.CreateCorrectionRequestCommand;
import com.bodego.platform.operations.domain.model.commands.ReviewCorrectionRequestCommand;

public interface CorrectionRequestCommandService {

    Long handle(CreateCorrectionRequestCommand command);

    Long handle(ReviewCorrectionRequestCommand command);
}
