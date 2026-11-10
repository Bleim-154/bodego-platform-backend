package com.bodego.platform.operations.application.internal.queryservices;

import com.bodego.platform.operations.domain.model.aggregates.StockAdjustment;
import com.bodego.platform.operations.domain.model.queries.*;
import com.bodego.platform.operations.domain.services.StockAdjustmentQueryService;
import com.bodego.platform.operations.infrastructure.persistence.jpa.repositories.StockAdjustmentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class StockAdjustmentQueryServiceImpl
        implements StockAdjustmentQueryService {

    private final StockAdjustmentRepository repository;

    public StockAdjustmentQueryServiceImpl(
            StockAdjustmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<StockAdjustment> handle(
            GetAllStockAdjustmentsQuery query) {
        return repository.findAll();
    }

    @Override
    public Optional<StockAdjustment> handle(
            GetStockAdjustmentByIdQuery query) {
        return repository.findById(query.id());
    }

    @Override
    public List<StockAdjustment> handle(
            GetStockAdjustmentsByBusinessIdQuery query) {
        return repository.findByBusinessId(query.businessId());
    }

    @Override
    public List<StockAdjustment> handle(
            GetStockAdjustmentsByLotIdQuery query) {
        return repository.findByLotId(query.lotId());
    }

    @Override
    public Optional<StockAdjustment> handle(
            GetStockAdjustmentByPhysicalCountIdQuery query) {
        return repository.findByPhysicalCountId(
                query.physicalCountId());
    }
}
