package com.bodego.platform.operations.application.internal.queryservices;

import com.bodego.platform.shared.application.security.*;

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
    private final BusinessAccess access;

    private final PhysicalCountRepository repository;

    public PhysicalCountQueryServiceImpl(PhysicalCountRepository repository, BusinessAccess access) {
        this.access = access;
        this.repository = repository;
    }

    @Override
    public List<PhysicalCount> handle(GetAllPhysicalCountsQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.OPERATIONS_READ);

        return repository.findByBusinessId(actor.businessId());
    }

    @Override
    public Optional<PhysicalCount> handle(GetPhysicalCountByIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.OPERATIONS_READ);

        return repository.findByIdAndBusinessId(query.id(), actor.businessId());
    }

    @Override
    public List<PhysicalCount> handle(
            GetPhysicalCountsByBusinessIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.OPERATIONS_READ);
        BusinessAccess.verifyClaim(actor, query.businessId());

        return repository.findByBusinessId(actor.businessId());
    }

    @Override
    public List<PhysicalCount> handle(
            GetPhysicalCountsByLotIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.OPERATIONS_READ);

        return repository.findByBusinessIdAndLotId(actor.businessId(), query.lotId());
    }
}
