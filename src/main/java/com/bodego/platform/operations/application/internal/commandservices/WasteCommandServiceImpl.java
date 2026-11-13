package com.bodego.platform.operations.application.internal.commandservices;

import com.bodego.platform.shared.application.security.*;

import com.bodego.platform.operations.application.internal.outboundservices.acl.ExternalMonitoringService;
import com.bodego.platform.operations.domain.model.aggregates.Waste;
import com.bodego.platform.operations.domain.model.commands.CreateInventoryMovementCommand;
import com.bodego.platform.operations.domain.model.commands.RegisterWasteCommand;
import com.bodego.platform.operations.domain.model.valueobjects.MovementType;
import com.bodego.platform.operations.domain.services.InventoryMovementCommandService;
import com.bodego.platform.operations.domain.services.WasteCommandService;
import com.bodego.platform.operations.infrastructure.persistence.jpa.repositories.WasteRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WasteCommandServiceImpl implements WasteCommandService {
    private final BusinessAccess access;

    private final WasteRepository wasteRepository;
    private final InventoryMovementCommandService movementCommandService;
    private final ExternalMonitoringService externalMonitoringService;

    public WasteCommandServiceImpl(
            WasteRepository wasteRepository,
            InventoryMovementCommandService movementCommandService,
            ExternalMonitoringService externalMonitoringService, BusinessAccess access) {
        this.access = access;
        this.wasteRepository = wasteRepository;
        this.movementCommandService = movementCommandService;
        this.externalMonitoringService = externalMonitoringService;
    }

    @Override
    @Transactional
    public Long handle(RegisterWasteCommand command) {
        if (command == null) throw new IllegalArgumentException("Command is required");
        var actor = access.write(BusinessPermission.WASTE_REGISTER, command.businessId());


        if (command == null) {
            throw new IllegalArgumentException(
                    "Waste command is required");
        }

        var waste = new Waste(
                actor.businessId(),
                command.lotId(),
                command.quantity(),
                command.reason(),
                command.observation()
        );

        String movementReason = "WASTE: " + waste.getReason().name();

        movementCommandService.handle(
                new CreateInventoryMovementCommand(
                        waste.getBusinessId(),
                        waste.getLotId(),
                        MovementType.EXIT,
                        waste.getQuantity(),
                        movementReason
                )
        );

        var savedWaste = wasteRepository.save(waste);

        externalMonitoringService.recordAction(
                savedWaste.getBusinessId(), "WASTE_REGISTERED",
                "Waste", savedWaste.getId(),
                "Waste of " + savedWaste.getQuantity() + " units for lot "
                        + savedWaste.getLotId() + ": " + savedWaste.getReason());

        return savedWaste.getId();
    }
}
