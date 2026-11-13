package com.bodego.platform.operations.application.internal.queryservices;

import com.bodego.platform.shared.application.security.*;

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
    private final BusinessAccess access;

    private final InventoryMovementRepository repository;

    public InventoryMovementQueryServiceImpl(
            InventoryMovementRepository repository, BusinessAccess access) {
        this.access = access;
        this.repository = repository;
    }

    @Override
    public List<InventoryMovement> handle(
            GetAllInventoryMovementsQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.OPERATIONS_READ);

        return repository.findByBusinessId(actor.businessId());
    }

    @Override
    public Optional<InventoryMovement> handle(
            GetInventoryMovementByIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.OPERATIONS_READ);

        return repository.findByIdAndBusinessId(query.id(), actor.businessId());
    }

    @Override
    public List<InventoryMovement> handle(
            GetInventoryMovementsByBusinessIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.OPERATIONS_READ);
        BusinessAccess.verifyClaim(actor, query.businessId());

        return repository.findByBusinessId(actor.businessId());
    }

    @Override
    public List<InventoryMovement> handle(
            GetInventoryMovementsByLotIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.OPERATIONS_READ);

        return repository.findByLotIdAndBusinessId(query.lotId(), actor.businessId());
    }
}
