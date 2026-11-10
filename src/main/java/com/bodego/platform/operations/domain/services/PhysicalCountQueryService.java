package com.bodego.platform.operations.domain.services;

import com.bodego.platform.operations.domain.model.aggregates.PhysicalCount;
import com.bodego.platform.operations.domain.model.queries.*;

import java.util.List;
import java.util.Optional;

public interface PhysicalCountQueryService {

    List<PhysicalCount> handle(GetAllPhysicalCountsQuery query);

    Optional<PhysicalCount> handle(GetPhysicalCountByIdQuery query);

    List<PhysicalCount> handle(GetPhysicalCountsByBusinessIdQuery query);

    List<PhysicalCount> handle(GetPhysicalCountsByLotIdQuery query);
}
