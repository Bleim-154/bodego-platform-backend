package com.bodego.platform.operations.domain.services;

import com.bodego.platform.operations.domain.model.aggregates.Waste;
import com.bodego.platform.operations.domain.model.queries.*;

import java.util.List;
import java.util.Optional;

public interface WasteQueryService {

    List<Waste> handle(GetAllWastesQuery query);

    Optional<Waste> handle(GetWasteByIdQuery query);

    List<Waste> handle(GetWastesByBusinessIdQuery query);

    List<Waste> handle(GetWastesByLotIdQuery query);
}
