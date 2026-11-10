package com.bodego.platform.strategicoffers.domain.services;

import com.bodego.platform.strategicoffers.domain.model.aggregates.Offer;
import com.bodego.platform.strategicoffers.domain.model.queries.*;

import java.util.*;

public interface OfferQueryService {

    Optional<Offer> handle(
            GetOfferByIdQuery query
    );

    List<Offer> handle(
            GetOffersQuery query
    );
}
