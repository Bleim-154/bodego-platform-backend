package com.bodego.platform.operations.application.internal.queryservices;

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

    private final WasteRepository repository;

    public WasteQueryServiceImpl(WasteRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Waste> handle(GetAllWastesQuery query) {
        return repository.findAll();
    }

    @Override
    public Optional<Waste> handle(GetWasteByIdQuery query) {
        return repository.findById(query.id());
    }

    @Override
    public List<Waste> handle(GetWastesByBusinessIdQuery query) {
        return repository.findByBusinessId(query.businessId());
    }

    @Override
    public List<Waste> handle(GetWastesByLotIdQuery query) {
        return repository.findByLotId(query.lotId());
    }
}
