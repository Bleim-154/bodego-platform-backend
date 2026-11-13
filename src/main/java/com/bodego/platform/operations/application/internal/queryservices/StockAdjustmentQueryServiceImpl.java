package com.bodego.platform.operations.application.internal.queryservices;

import com.bodego.platform.shared.application.security.*;

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
    private final BusinessAccess access;

    private final StockAdjustmentRepository repository;

    public StockAdjustmentQueryServiceImpl(
            StockAdjustmentRepository repository, BusinessAccess access) {
        this.access = access;
        this.repository = repository;
    }

    @Override
    public List<StockAdjustment> handle(
            GetAllStockAdjustmentsQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.STOCK_ADJUSTMENT);

        return repository.findByBusinessId(actor.businessId());
    }

    @Override
    public Optional<StockAdjustment> handle(
            GetStockAdjustmentByIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.STOCK_ADJUSTMENT);

        return repository.findByIdAndBusinessId(query.id(), actor.businessId());
    }

    @Override
    public List<StockAdjustment> handle(
            GetStockAdjustmentsByBusinessIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.STOCK_ADJUSTMENT);
        BusinessAccess.verifyClaim(actor, query.businessId());

        return repository.findByBusinessId(actor.businessId());
    }

    @Override
    public List<StockAdjustment> handle(
            GetStockAdjustmentsByLotIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.STOCK_ADJUSTMENT);

        return repository.findByLotIdAndBusinessId(query.lotId(), actor.businessId());
    }

    @Override
    public Optional<StockAdjustment> handle(
            GetStockAdjustmentByPhysicalCountIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.STOCK_ADJUSTMENT);

        return repository.findByPhysicalCountIdAndBusinessId(query.physicalCountId(), actor.businessId());
    }
}
