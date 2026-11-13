package com.bodego.platform.inventory.application.internal.queryservices;

import com.bodego.platform.shared.application.security.*;

import com.bodego.platform.inventory.domain.model.aggregates.Lot;
import com.bodego.platform.inventory.domain.model.queries.*;
import com.bodego.platform.inventory.domain.services.LotQueryService;
import com.bodego.platform.inventory.infrastructure.persistence.jpa.repositories.LotRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class LotQueryServiceImpl implements LotQueryService {
    private final BusinessAccess access;

    private final LotRepository lotRepository;

    public LotQueryServiceImpl(LotRepository lotRepository, BusinessAccess access) {
        this.access = access;
        this.lotRepository = lotRepository;
    }

    @Override
    public List<Lot> handle(GetAllLotsQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.INVENTORY_READ);

        return lotRepository.findByBusinessId(actor.businessId());
    }

    @Override
    public Optional<Lot> handle(GetLotByIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.INVENTORY_READ);

        return lotRepository.findByIdAndBusinessId(query.lotId(), actor.businessId());
    }

    @Override
    public List<Lot> handle(GetLotsByProductIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.INVENTORY_READ);

        return lotRepository.findByProductIdAndBusinessId(query.productId(), actor.businessId());
    }

    @Override
    public List<Lot> handle(GetLotsByBusinessIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.INVENTORY_READ);
        BusinessAccess.verifyClaim(actor, query.businessId());

        return lotRepository.findByBusinessId(actor.businessId());
    }
}
