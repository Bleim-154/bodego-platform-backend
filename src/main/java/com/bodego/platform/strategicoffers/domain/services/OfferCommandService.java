package com.bodego.platform.strategicoffers.domain.services;

import com.bodego.platform.strategicoffers.domain.model.aggregates.Offer;
import com.bodego.platform.strategicoffers.domain.model.commands.*;

import java.util.*;

public interface OfferCommandService {

    Long handle(
            CreateOfferCommand command
    );

    Optional<Offer> handle(
            UpdateOfferCommand command
    );

    Optional<Offer> handle(
            ActivateOfferCommand command
    );

    Optional<Offer> handle(
            DeactivateOfferCommand command
    );
}
