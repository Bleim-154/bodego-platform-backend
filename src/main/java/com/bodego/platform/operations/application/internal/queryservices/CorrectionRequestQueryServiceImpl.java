package com.bodego.platform.operations.application.internal.queryservices;

import com.bodego.platform.shared.application.security.*;

import com.bodego.platform.operations.domain.model.aggregates.CorrectionRequest;
import com.bodego.platform.operations.domain.model.queries.*;
import com.bodego.platform.operations.domain.services.CorrectionRequestQueryService;
import com.bodego.platform.operations.infrastructure.persistence.jpa.repositories.CorrectionRequestRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CorrectionRequestQueryServiceImpl
        implements CorrectionRequestQueryService {
    private final BusinessAccess access;

    private final CorrectionRequestRepository repository;

    public CorrectionRequestQueryServiceImpl(
            CorrectionRequestRepository repository, BusinessAccess access) {
        this.access = access;
        this.repository = repository;
    }

    @Override
    public List<CorrectionRequest> handle(
            GetAllCorrectionRequestsQuery query
    ) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.OPERATIONS_READ);

        return repository.findByBusinessId(actor.businessId());
    }

    @Override
    public Optional<CorrectionRequest> handle(
            GetCorrectionRequestByIdQuery query
    ) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.OPERATIONS_READ);

        return repository.findByIdAndBusinessId(query.id(), actor.businessId());
    }

    @Override
    public List<CorrectionRequest> handle(
            GetCorrectionRequestsByBusinessIdQuery query
    ) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.OPERATIONS_READ);
        BusinessAccess.verifyClaim(actor, query.businessId());

        return repository.findByBusinessId(actor.businessId());
    }

    @Override
    public List<CorrectionRequest> handle(
            GetCorrectionRequestsByPhysicalCountIdQuery query
    ) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.OPERATIONS_READ);

        return repository.findByPhysicalCountIdAndBusinessId(query.physicalCountId(), actor.businessId());
    }

    @Override
    public List<CorrectionRequest> handle(
            GetCorrectionRequestsByBusinessAndStatusQuery query
    ) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.OPERATIONS_READ);
        BusinessAccess.verifyClaim(actor, query.businessId());

        return repository.findByBusinessIdAndStatus(
                actor.businessId(),
                query.status()
        );
    }
}
