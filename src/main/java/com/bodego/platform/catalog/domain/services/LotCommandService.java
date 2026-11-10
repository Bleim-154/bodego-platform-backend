package com.bodego.platform.catalog.domain.services;

import com.bodego.platform.catalog.domain.model.aggregates.Lot;
import com.bodego.platform.catalog.domain.model.commands.*;

import java.util.Optional;

public interface LotCommandService {

    Long handle(CreateLotCommand command);

    Optional<Lot> handle(UpdateLotCommand command);

    Optional<Lot> handle(IncreaseLotQuantityCommand command);

    Optional<Lot> handle(DecreaseLotQuantityCommand command);

    void handle(ValidateLotStockCommand command);
}
