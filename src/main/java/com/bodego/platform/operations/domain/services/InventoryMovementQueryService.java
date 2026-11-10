package com.bodego.platform.operations.domain.services;

import com.bodego.platform.operations.domain.model.aggregates.InventoryMovement;
import com.bodego.platform.operations.domain.model.queries.*;

import java.util.List;
import java.util.Optional;

public interface InventoryMovementQueryService {

    List<InventoryMovement> handle(
            GetAllInventoryMovementsQuery query);

    Optional<InventoryMovement> handle(
            GetInventoryMovementByIdQuery query);

    List<InventoryMovement> handle(
            GetInventoryMovementsByBusinessIdQuery query);

    List<InventoryMovement> handle(
            GetInventoryMovementsByLotIdQuery query);
}
