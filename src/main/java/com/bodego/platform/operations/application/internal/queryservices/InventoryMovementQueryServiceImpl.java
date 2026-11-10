package com.bodego.platform.operations.application.internal.queryservices;

import com.bodego.platform.operations.domain.model.aggregates.InventoryMovement;
import com.bodego.platform.operations.domain.model.queries.*;
import com.bodego.platform.operations.domain.services.InventoryMovementQueryService;
import com.bodego.platform.operations.infrastructure.persistence.jpa.repositories.InventoryMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class InventoryMovementQueryServiceImpl
        implements InventoryMovementQueryService {

    private final InventoryMovementRepository repository;

    public InventoryMovementQueryServiceImpl(
            InventoryMovementRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<InventoryMovement> handle(
            GetAllInventoryMovementsQuery query) {
        return repository.findAll();
    }

    @Override
    public Optional<InventoryMovement> handle(
            GetInventoryMovementByIdQuery query) {
        return repository.findById(query.id());
    }

    @Override
    public List<InventoryMovement> handle(
            GetInventoryMovementsByBusinessIdQuery query) {
        return repository.findByBusinessId(query.businessId());
    }

    @Override
    public List<InventoryMovement> handle(
            GetInventoryMovementsByLotIdQuery query) {
        return repository.findByLotId(query.lotId());
    }
}
