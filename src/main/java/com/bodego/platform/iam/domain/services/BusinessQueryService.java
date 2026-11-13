package com.bodego.platform.iam.domain.services;

import com.bodego.platform.iam.domain.model.aggregates.Business;
import com.bodego.platform.iam.domain.model.queries.GetBusinessByIdQuery;
import com.bodego.platform.iam.domain.model.queries.IsBusinessActiveQuery;
import java.util.Optional;

public interface BusinessQueryService {
    Optional<Business> handle(GetBusinessByIdQuery query);
    boolean handle(IsBusinessActiveQuery query);
}
