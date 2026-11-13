package com.bodego.platform.strategicoffers.application.internal.queryservices;

import com.bodego.platform.shared.application.security.*;

import com.bodego.platform.strategicoffers.domain.model.aggregates.*;
import com.bodego.platform.strategicoffers.domain.model.queries.*;
import com.bodego.platform.strategicoffers.domain.services.*;
import com.bodego.platform.strategicoffers.infrastructure.persistence.jpa.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class OfferQueryServiceImpl implements OfferQueryService {
    private final BusinessAccess access;

    private final OfferRepository repository;

    public OfferQueryServiceImpl(
            OfferRepository repository, BusinessAccess access) {
        this.access = access;
        this.repository = repository;
    }

    @Override
    public Optional<Offer> handle(
            GetOfferByIdQuery query
    ) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.STRATEGIC_OFFERS);


        return repository.findByIdAndBusinessId(query.offerId(), actor.businessId());
    }

    @Override
    public List<Offer> handle(
            GetOffersQuery query
    ) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.STRATEGIC_OFFERS);
        BusinessAccess.verifyClaim(actor, query.businessId());


        return repository.findByFilters(actor.businessId(), query.productId(), query.active());
    }
}
