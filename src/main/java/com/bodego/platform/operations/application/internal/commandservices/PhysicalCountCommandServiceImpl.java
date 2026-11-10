package com.bodego.platform.operations.application.internal.commandservices;

import com.bodego.platform.catalog.domain.model.aggregates.Lot;
import com.bodego.platform.catalog.domain.model.queries.GetLotByIdQuery;
import com.bodego.platform.catalog.domain.services.LotQueryService;
import com.bodego.platform.operations.domain.model.aggregates.PhysicalCount;
import com.bodego.platform.operations.domain.model.commands.CreatePhysicalCountCommand;
import com.bodego.platform.operations.infrastructure.persistence.jpa.repositories.PhysicalCountRepository;
import org.springframework.stereotype.Service;
import com.bodego.platform.operations.domain.services.PhysicalCountCommandService;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhysicalCountCommandServiceImpl implements PhysicalCountCommandService {

    private final PhysicalCountRepository physicalCountRepository;
    private final LotQueryService lotQueryService;

    public PhysicalCountCommandServiceImpl(PhysicalCountRepository physicalCountRepository, LotQueryService lotQueryService) {
        this.physicalCountRepository = physicalCountRepository;
        this.lotQueryService = lotQueryService;
    }

    @Override
    @Transactional
    public Long handle(CreatePhysicalCountCommand command) {
        Lot lot = lotQueryService.handle(new GetLotByIdQuery(command.lotId())).orElseThrow(() -> new IllegalArgumentException("Lot not found"));

        var physicalCount = new PhysicalCount(lot.getBusinessId(), lot.getId(), command.userId(), lot.getQuantity(), command.physicalQuantity(), command.date(), command.observation());

        return physicalCountRepository.save(physicalCount).getId();
    }
}