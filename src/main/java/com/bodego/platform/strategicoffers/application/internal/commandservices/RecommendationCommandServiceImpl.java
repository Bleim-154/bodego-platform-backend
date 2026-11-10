package com.bodego.platform.strategicoffers.application.internal.commandservices;

import com.bodego.platform.strategicoffers.application.internal.outboundservices.acl.ExternalCatalogService;
import com.bodego.platform.strategicoffers.application.internal.outboundservices.acl.ExternalMonitoringService;
import com.bodego.platform.strategicoffers.domain.model.aggregates.Recommendation;
import com.bodego.platform.strategicoffers.domain.model.commands.*;
import com.bodego.platform.strategicoffers.domain.model.valueobjects.RecommendationType;
import com.bodego.platform.strategicoffers.domain.services.RecommendationCommandService;
import com.bodego.platform.strategicoffers.infrastructure.persistence.jpa.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class RecommendationCommandServiceImpl implements RecommendationCommandService {

    private final RecommendationRepository repository;
    private final ExternalCatalogService catalog;
    private final ExternalMonitoringService monitoring;
    private final RecommendationGenerationLock generationLock;
    private final Clock clock;

    @Autowired
    public RecommendationCommandServiceImpl(
            RecommendationRepository repository,
            ExternalCatalogService catalog,
            RecommendationGenerationLock generationLock,
            ExternalMonitoringService monitoring
    ) {
        this(repository, catalog, generationLock, monitoring, Clock.systemUTC());
    }

    RecommendationCommandServiceImpl(
            RecommendationRepository repository,
            ExternalCatalogService catalog,
            RecommendationGenerationLock generationLock,
            ExternalMonitoringService monitoring,
            Clock clock
    ) {
        this.repository = repository;
        this.catalog = catalog;
        this.monitoring = monitoring;
        this.generationLock = generationLock;
        this.clock = clock;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<Recommendation> handle(
            GenerateRecommendationsCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("Command is required");
        }

        var businessId = command.businessId();
        generationLock.acquire(businessId);
        var evaluatedAt = clock.instant();
        var today = evaluatedAt.atZone(clock.getZone()).toLocalDate();
        var generated = new ArrayList<Recommendation>();
        for (var product : catalog.getProductsByBusinessId(businessId)) {
            if (!businessId.equals(product.businessId())) {
                throw new IllegalArgumentException("Product does not belong to the business");
            }
            if (!Boolean.TRUE.equals(product.active())) {
                continue;
            }
            long stock = 0;
            for (var lot : catalog.getLotsByProductId(businessId, product.id())) {
                if (!businessId.equals(lot.businessId()) || !product.id().equals(lot.productId())) {
                    throw new IllegalArgumentException("Lot does not belong to the business and product");
                }
                stock += lot.quantity().longValue();
                var expiration = lot.expirationDate();
                if (lot.quantity() > 0 && expiration != null && !expiration.isBefore(today)
                        && !expiration.isAfter(today.plusDays(30))) {
                    generateIfAbsent(generated, businessId, product.id(), lot.id(), RecommendationType.EXPIRING,
                            "Lot " + lot.id() + " expires on " + expiration, evaluatedAt);
                }
            }
            if (stock < product.minimumStock()) {
                generateIfAbsent(generated, businessId, product.id(), null, RecommendationType.RESTOCK,
                        "Product " + product.id() + " stock " + stock + " is below minimum " + product.minimumStock(), evaluatedAt);
            }
            if (product.minimumStock() > 0 && stock > 3L * product.minimumStock()) {
                generateIfAbsent(generated, businessId, product.id(), null, RecommendationType.OVERSTOCK,
                        "Product " + product.id() + " stock " + stock + " exceeds three times its minimum", evaluatedAt);
            }
        }

        return List.copyOf(generated);
    }

    @Override
    @Transactional
    public Optional<Recommendation> handle(
            DismissRecommendationCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("Command is required");
        }

        return repository.findWithLockById(command.recommendationId())
                .map(recommendation -> {
                    if (recommendation.dismiss()) {
                        repository.save(recommendation);
                        monitoring.recordAction(recommendation.getBusinessId(), "RECOMMENDATION_DISMISSED", "Recommendation",
                                recommendation.getId(), "Recommendation dismissed for product " + recommendation.getProductId());
                    }
                    return recommendation;
                });
    }

    private void generateIfAbsent(
            List<Recommendation> generated,
            Long businessId,
            Long productId,
            Long lotId,
            RecommendationType type,
            String message,
            Instant evaluatedAt
    ) {
        if (repository.existsByBusinessIdAndProductIdAndLotIdAndTypeAndDismissedFalse(businessId, productId, lotId, type)) {
            return;
        }

        var saved = repository.save(new Recommendation(
                businessId,
                productId,
                lotId,
                type,
                message,
                evaluatedAt
        ));
        monitoring.recordAction(saved.getBusinessId(), "RECOMMENDATION_GENERATED", "Recommendation", saved.getId(),
                "Recommendation " + saved.getType() + " for product " + saved.getProductId());
        generated.add(saved);
    }
}
