package com.bodego.platform.monitoring.application.internal.commandservices;

import com.bodego.platform.shared.application.security.*;

import com.bodego.platform.monitoring.application.internal.outboundservices.acl.ExternalCatalogService;
import com.bodego.platform.monitoring.domain.model.aggregates.Alert;
import com.bodego.platform.monitoring.domain.model.commands.GenerateAlertsCommand;
import com.bodego.platform.monitoring.domain.model.valueobjects.AlertSeverity;
import com.bodego.platform.monitoring.domain.model.valueobjects.AlertType;
import com.bodego.platform.monitoring.domain.services.AlertGenerationService;
import com.bodego.platform.monitoring.infrastructure.persistence.jpa.repositories.AlertGenerationLock;
import com.bodego.platform.monitoring.infrastructure.persistence.jpa.repositories.AlertRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class AlertGenerationServiceImpl implements AlertGenerationService {
    private final BusinessAccess access;

    private final AlertRepository alertRepository;
    private final ExternalCatalogService externalCatalogService;
    private final AlertGenerationLock generationLock;
    private final Clock clock;

    @Autowired
    public AlertGenerationServiceImpl(
            AlertRepository alertRepository,
            ExternalCatalogService externalCatalogService,
            AlertGenerationLock generationLock, BusinessAccess access) {
        this(alertRepository, externalCatalogService, generationLock, Clock.systemUTC(), access);
    }

    AlertGenerationServiceImpl(
            AlertRepository alertRepository,
            ExternalCatalogService externalCatalogService,
            AlertGenerationLock generationLock,
            Clock clock, BusinessAccess access) {
        this.access = access;
        this.alertRepository = alertRepository;
        this.externalCatalogService = externalCatalogService;
        this.generationLock = generationLock;
        this.clock = clock;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<Alert> handle(GenerateAlertsCommand command) {
        if (command == null) throw new IllegalArgumentException("Command is required");
        var actor = access.write(BusinessPermission.ALERT_MANAGE, command.businessId());


        Long businessId = actor.businessId();
        generationLock.acquire(businessId);
        var today = LocalDate.now(clock);
        var generated = new ArrayList<Alert>();

        for (var product : externalCatalogService.getProductsByBusinessId(businessId)) {
            if (!businessId.equals(product.businessId())) {
                throw new IllegalArgumentException("Product does not belong to the business");
            }

            var lots = externalCatalogService.getLotsByProductId(businessId, product.id());
            long totalStock = 0;
            for (var lot : lots) {
                if (!businessId.equals(lot.businessId()) || !product.id().equals(lot.productId())) {
                    throw new IllegalArgumentException("Lot does not belong to the business and product");
                }
                totalStock += lot.quantity().longValue();

                var expirationDate = lot.expirationDate();
                if (expirationDate == null) {
                    continue;
                }
                if (expirationDate.isBefore(today)) {
                    generateIfAbsent(generated, businessId, product.id(), lot.id(),
                            AlertType.EXPIRED, AlertSeverity.HIGH,
                            "Lot " + lot.id() + " expired on " + expirationDate);
                } else if (!expirationDate.isAfter(today.plusDays(7))) {
                    generateIfAbsent(generated, businessId, product.id(), lot.id(),
                            AlertType.EXPIRING_SOON, AlertSeverity.MEDIUM,
                            "Lot " + lot.id() + " expires on " + expirationDate);
                }
            }

            if (totalStock < product.minimumStock()) {
                generateIfAbsent(generated, businessId, product.id(), null,
                        AlertType.LOW_STOCK, AlertSeverity.MEDIUM,
                        "Product " + product.id() + " stock " + totalStock
                                + " is below minimum " + product.minimumStock());
            }
        }

        return List.copyOf(generated);
    }

    private void generateIfAbsent(
            List<Alert> generated,
            Long businessId,
            Long productId,
            Long lotId,
            AlertType type,
            AlertSeverity severity,
            String message
    ) {
        if (!alertRepository.existsByBusinessIdAndProductIdAndLotIdAndTypeAndResolvedFalse(
                businessId, productId, lotId, type)) {
            generated.add(alertRepository.save(
                    new Alert(businessId, productId, lotId, type, severity, message)));
        }
    }
}
