package com.bodego.platform.operations.domain.services;

import com.bodego.platform.operations.domain.model.aggregates.CorrectionRequest;
import com.bodego.platform.operations.domain.model.queries.*;

import java.util.List;
import java.util.Optional;

public interface CorrectionRequestQueryService {

    List<CorrectionRequest> handle(GetAllCorrectionRequestsQuery query);

    Optional<CorrectionRequest> handle(GetCorrectionRequestByIdQuery query);

    List<CorrectionRequest> handle(GetCorrectionRequestsByBusinessIdQuery query);

    List<CorrectionRequest> handle(GetCorrectionRequestsByPhysicalCountIdQuery query);

    List<CorrectionRequest> handle(GetCorrectionRequestsByBusinessAndStatusQuery query);
}
