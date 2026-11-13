package com.bodego.platform.operations.application.internal.queryservices;

import com.bodego.platform.shared.application.security.*;

import com.bodego.platform.operations.domain.model.aggregates.Waste;
import com.bodego.platform.operations.domain.model.queries.*;
import com.bodego.platform.operations.domain.services.WasteQueryService;
import com.bodego.platform.operations.infrastructure.persistence.jpa.repositories.WasteRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class WasteQueryServiceImpl implements WasteQueryService {
    private final BusinessAccess access;

    private final WasteRepository repository;

    public WasteQueryServiceImpl(WasteRepository repository, BusinessAccess access) {
        this.access = access;
        this.repository = repository;
    }

    @Override
    public List<Waste> handle(GetAllWastesQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.OPERATIONS_READ);

        return repository.findByBusinessId(actor.businessId());
    }

    @Override
    public Optional<Waste> handle(GetWasteByIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.OPERATIONS_READ);

        return repository.findByIdAndBusinessId(query.id(), actor.businessId());
    }

    @Override
    public List<Waste> handle(GetWastesByBusinessIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.OPERATIONS_READ);
        BusinessAccess.verifyClaim(actor, query.businessId());

        return repository.findByBusinessId(actor.businessId());
    }

    @Override
    public List<Waste> handle(GetWastesByLotIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.OPERATIONS_READ);

        return repository.findByBusinessIdAndLotId(actor.businessId(), query.lotId());
    }
}
