package com.bodego.platform.strategicoffers.application.internal.commandservices;

import com.bodego.platform.strategicoffers.application.internal.outboundservices.acl.ExternalCatalogService;
import com.bodego.platform.strategicoffers.application.internal.outboundservices.acl.ExternalMonitoringService;
import com.bodego.platform.strategicoffers.domain.model.aggregates.Offer;
import com.bodego.platform.strategicoffers.domain.model.commands.*;
import com.bodego.platform.strategicoffers.domain.services.OfferCommandService;
import com.bodego.platform.strategicoffers.infrastructure.persistence.jpa.repositories.OfferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class OfferCommandServiceImpl implements OfferCommandService {

    private final OfferRepository repository;
    private final ExternalCatalogService catalog;
    private final ExternalMonitoringService monitoring;

    public OfferCommandServiceImpl(
            OfferRepository repository,
            ExternalCatalogService catalog,
            ExternalMonitoringService monitoring
    ) {
        this.repository = repository;
        this.catalog = catalog;
        this.monitoring = monitoring;
    }

    @Override
    public Long handle(
            CreateOfferCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("Command is required");
        }

        var offer = new Offer(
                command.businessId(),
                command.productId(),
                command.title(),
                command.discountPercentage(),
                command.startDate(),
                command.endDate()
        );
        catalog.validateActiveProduct(offer.getBusinessId(), offer.getProductId());
        var saved = repository.save(offer);
        monitoring.recordAction(saved.getBusinessId(), "OFFER_CREATED", "Offer", saved.getId(),
                "Offer created for product " + saved.getProductId());
        return saved.getId();
    }

    @Override
    public Optional<Offer> handle(
            UpdateOfferCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("Command is required");
        }

        return repository.findWithLockById(command.offerId())
                .map(offer -> {
                    if (offer.updateInformation(command.title(), command.discountPercentage(), command.startDate(), command.endDate())) {
                        repository.save(offer);
                        monitoring.recordAction(offer.getBusinessId(), "OFFER_UPDATED", "Offer", offer.getId(),
                                "Offer updated for product " + offer.getProductId());
                    }
                    return offer;
                });
    }

    @Override
    public Optional<Offer> handle(
            ActivateOfferCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("Command is required");
        }

        return repository.findWithLockById(command.offerId())
                .map(offer -> {
                    if (!offer.isActive()) {
                        catalog.validateActiveProduct(offer.getBusinessId(), offer.getProductId());
                        offer.activate();
                        repository.save(offer);
                        monitoring.recordAction(offer.getBusinessId(), "OFFER_ACTIVATED", "Offer", offer.getId(),
                                "Offer activated for product " + offer.getProductId());
                    }
                    return offer;
                });
    }

    @Override
    public Optional<Offer> handle(
            DeactivateOfferCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("Command is required");
        }

        return repository.findWithLockById(command.offerId())
                .map(offer -> {
                    if (offer.deactivate()) {
                        repository.save(offer);
                        monitoring.recordAction(offer.getBusinessId(), "OFFER_DEACTIVATED", "Offer", offer.getId(),
                                "Offer deactivated for product " + offer.getProductId());
                    }
                    return offer;
                });
    }
}
