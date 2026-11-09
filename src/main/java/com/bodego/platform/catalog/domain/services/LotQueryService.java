package com.bodego.platform.catalog.domain.services;

import com.bodego.platform.catalog.domain.model.aggregates.Lot;
import com.bodego.platform.catalog.domain.model.queries.*;

import java.util.List;
import java.util.Optional;

public interface LotQueryService {

    List<Lot> handle(GetAllLotsQuery query);

    Optional<Lot> handle(GetLotByIdQuery query);

    List<Lot> handle(GetLotsByProductIdQuery query);

    List<Lot> handle(GetLotsByBusinessIdQuery query);
}
