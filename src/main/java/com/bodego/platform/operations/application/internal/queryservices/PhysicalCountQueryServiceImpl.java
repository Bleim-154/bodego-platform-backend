package com.bodego.platform.operations.application.internal.queryservices;

import com.bodego.platform.operations.domain.model.aggregates.PhysicalCount;
import com.bodego.platform.operations.domain.model.queries.*;
import com.bodego.platform.operations.domain.services.PhysicalCountQueryService;
import com.bodego.platform.operations.infrastructure.persistence.jpa.repositories.PhysicalCountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class PhysicalCountQueryServiceImpl
        implements PhysicalCountQueryService {

    private final PhysicalCountRepository repository;

    public PhysicalCountQueryServiceImpl(PhysicalCountRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<PhysicalCount> handle(GetAllPhysicalCountsQuery query) {
        return repository.findAll();
    }

    @Override
    public Optional<PhysicalCount> handle(GetPhysicalCountByIdQuery query) {
        return repository.findById(query.id());
    }

    @Override
    public List<PhysicalCount> handle(
            GetPhysicalCountsByBusinessIdQuery query) {
        return repository.findByBusinessId(query.businessId());
    }

    @Override
    public List<PhysicalCount> handle(
            GetPhysicalCountsByLotIdQuery query) {
        return repository.findByLotId(query.lotId());
    }
}
