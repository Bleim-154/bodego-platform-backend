package com.bodego.platform.catalog.application.internal.queryservices;

import com.bodego.platform.catalog.domain.model.aggregates.Lot;
import com.bodego.platform.catalog.domain.model.queries.*;
import com.bodego.platform.catalog.domain.services.LotQueryService;
import com.bodego.platform.catalog.infrastructure.persistence.jpa.repositories.LotRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class LotQueryServiceImpl implements LotQueryService {

    private final LotRepository lotRepository;

    public LotQueryServiceImpl(LotRepository lotRepository) {
        this.lotRepository = lotRepository;
    }

    @Override
    public List<Lot> handle(GetAllLotsQuery query) {
        return lotRepository.findAll();
    }

    @Override
    public Optional<Lot> handle(GetLotByIdQuery query) {
        return lotRepository.findById(query.lotId());
    }

    @Override
    public List<Lot> handle(GetLotsByProductIdQuery query) {
        return lotRepository.findByProductId(query.productId());
    }

    @Override
    public List<Lot> handle(GetLotsByBusinessIdQuery query) {
        return lotRepository.findByBusinessId(query.businessId());
    }
}
