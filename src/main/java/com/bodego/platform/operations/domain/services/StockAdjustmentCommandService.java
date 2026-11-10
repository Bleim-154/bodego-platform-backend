package com.bodego.platform.operations.domain.services;

import com.bodego.platform.operations.domain.model.commands.ApplyStockAdjustmentCommand;

public interface StockAdjustmentCommandService {

    Long handle(ApplyStockAdjustmentCommand command);
}
