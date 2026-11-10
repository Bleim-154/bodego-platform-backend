package com.bodego.platform.operations.application.internal.commandservices;

import com.bodego.platform.operations.application.internal.outboundservices.acl.ExternalCatalogService;
import com.bodego.platform.operations.domain.model.aggregates.InventoryMovement;
import com.bodego.platform.operations.domain.model.commands.CreateInventoryMovementCommand;
import com.bodego.platform.operations.domain.services.InventoryMovementCommandService;
import com.bodego.platform.operations.infrastructure.persistence.jpa.repositories.InventoryMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryMovementCommandServiceImpl
        implements InventoryMovementCommandService {

    private final InventoryMovementRepository movementRepository;
    private final ExternalCatalogService externalCatalogService;

    public InventoryMovementCommandServiceImpl(
            InventoryMovementRepository movementRepository,
            ExternalCatalogService externalCatalogService) {
        this.movementRepository = movementRepository;
        this.externalCatalogService = externalCatalogService;
    }

    @Override
    @Transactional
    public Long handle(CreateInventoryMovementCommand command) {

        var movement = new InventoryMovement(
                command.businessId(),
                command.lotId(),
                command.type(),
                command.quantity(),
                command.reason()
        );

        externalCatalogService.registerMovement(
                movement.getBusinessId(),
                movement.getLotId(),
                movement.getType(),
                movement.getQuantity()
        );

        var savedMovement = movementRepository.save(movement);

        return savedMovement.getId();
    }
}
