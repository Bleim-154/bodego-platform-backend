package com.bodego.platform.operations.domain.services;

import com.bodego.platform.operations.domain.model.aggregates.StockAdjustment;
import com.bodego.platform.operations.domain.model.queries.*;

import java.util.List;
import java.util.Optional;

public interface StockAdjustmentQueryService {

    List<StockAdjustment> handle(GetAllStockAdjustmentsQuery query);

    Optional<StockAdjustment> handle(GetStockAdjustmentByIdQuery query);

    List<StockAdjustment> handle(
            GetStockAdjustmentsByBusinessIdQuery query);

    List<StockAdjustment> handle(
            GetStockAdjustmentsByLotIdQuery query);

    Optional<StockAdjustment> handle(GetStockAdjustmentByPhysicalCountIdQuery query);
}
