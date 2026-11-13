package com.bodego.platform.operations.application.internal.commandservices;

import com.bodego.platform.shared.application.security.*;

import com.bodego.platform.operations.application.internal.outboundservices.acl.ExternalInventoryService;
import com.bodego.platform.operations.application.internal.outboundservices.acl.ExternalMonitoringService;
import com.bodego.platform.operations.domain.model.aggregates.InventoryMovement;
import com.bodego.platform.operations.domain.model.commands.CreateInventoryMovementCommand;
import com.bodego.platform.operations.domain.services.InventoryMovementCommandService;
import com.bodego.platform.operations.infrastructure.persistence.jpa.repositories.InventoryMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryMovementCommandServiceImpl
        implements InventoryMovementCommandService {
    private final BusinessAccess access;

    private final InventoryMovementRepository movementRepository;
    private final ExternalInventoryService externalCatalogService;
    private final ExternalMonitoringService externalMonitoringService;

    public InventoryMovementCommandServiceImpl(
            InventoryMovementRepository movementRepository,
            ExternalInventoryService externalCatalogService,
            ExternalMonitoringService externalMonitoringService, BusinessAccess access) {
        this.access = access;
        this.movementRepository = movementRepository;
        this.externalCatalogService = externalCatalogService;
        this.externalMonitoringService = externalMonitoringService;
    }

    @Override
    @Transactional
    public Long handle(CreateInventoryMovementCommand command) {
        if (command == null) throw new IllegalArgumentException("Command is required");
        var actor = access.write(BusinessPermission.MOVEMENT_REGISTER, command.businessId());


        var movement = new InventoryMovement(
                actor.businessId(),
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

        externalMonitoringService.recordAction(
                savedMovement.getBusinessId(), "INVENTORY_MOVEMENT_REGISTERED",
                "InventoryMovement", savedMovement.getId(),
                "Movement " + savedMovement.getType() + " of " + savedMovement.getQuantity()
                        + " units for lot " + savedMovement.getLotId());

        return savedMovement.getId();
    }
}
