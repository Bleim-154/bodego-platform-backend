package com.bodego.platform.operations.application.internal.queryservices;

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

    private final CorrectionRequestRepository repository;

    public CorrectionRequestQueryServiceImpl(
            CorrectionRequestRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public List<CorrectionRequest> handle(
            GetAllCorrectionRequestsQuery query
    ) {
        return repository.findAll();
    }

    @Override
    public Optional<CorrectionRequest> handle(
            GetCorrectionRequestByIdQuery query
    ) {
        return repository.findById(query.id());
    }

    @Override
    public List<CorrectionRequest> handle(
            GetCorrectionRequestsByBusinessIdQuery query
    ) {
        return repository.findByBusinessId(query.businessId());
    }

    @Override
    public List<CorrectionRequest> handle(
            GetCorrectionRequestsByPhysicalCountIdQuery query
    ) {
        return repository.findByPhysicalCountId(query.physicalCountId());
    }

    @Override
    public List<CorrectionRequest> handle(
            GetCorrectionRequestsByBusinessAndStatusQuery query
    ) {
        return repository.findByBusinessIdAndStatus(
                query.businessId(),
                query.status()
        );
    }
}
