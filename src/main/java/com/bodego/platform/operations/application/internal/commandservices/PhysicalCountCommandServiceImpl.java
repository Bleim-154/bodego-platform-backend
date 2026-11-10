package com.bodego.platform.operations.application.internal.commandservices;

import com.bodego.platform.operations.application.internal.outboundservices.acl.ExternalCatalogService;
import com.bodego.platform.operations.domain.model.aggregates.PhysicalCount;
import com.bodego.platform.operations.domain.model.commands.CreatePhysicalCountCommand;
import com.bodego.platform.operations.domain.services.PhysicalCountCommandService;
import com.bodego.platform.operations.infrastructure.persistence.jpa.repositories.PhysicalCountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhysicalCountCommandServiceImpl
        implements PhysicalCountCommandService {

    private final PhysicalCountRepository physicalCountRepository;
    private final ExternalCatalogService externalCatalogService;

    public PhysicalCountCommandServiceImpl(
            PhysicalCountRepository physicalCountRepository,
            ExternalCatalogService externalCatalogService) {
        this.physicalCountRepository = physicalCountRepository;
        this.externalCatalogService = externalCatalogService;
    }

    @Override
    @Transactional
    public Long handle(CreatePhysicalCountCommand command) {

        if (command == null) {
            throw new IllegalArgumentException(
                    "Physical count command is required");
        }

        if (command.countedQuantity() == null
                || command.countedQuantity() < 0) {
            throw new IllegalArgumentException(
                    "Counted quantity cannot be negative");
        }

        Integer systemQuantity =
                externalCatalogService.getLotQuantity(
                        command.businessId(),
                        command.lotId()
                );

        var physicalCount = new PhysicalCount(
                command.businessId(),
                command.lotId(),
                systemQuantity,
                command.countedQuantity(),
                command.observation()
        );

        var savedPhysicalCount =
                physicalCountRepository.save(physicalCount);

        return savedPhysicalCount.getId();
    }
}
