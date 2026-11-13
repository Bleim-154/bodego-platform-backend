package com.bodego.platform.monitoring.application.internal.queryservices;

import com.bodego.platform.shared.application.security.*;

import com.bodego.platform.monitoring.domain.model.aggregates.Alert;
import com.bodego.platform.monitoring.domain.model.queries.GetAlertByIdQuery;
import com.bodego.platform.monitoring.domain.model.queries.GetAlertsQuery;
import com.bodego.platform.monitoring.domain.services.AlertQueryService;
import com.bodego.platform.monitoring.infrastructure.persistence.jpa.repositories.AlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AlertQueryServiceImpl implements AlertQueryService {
    private final BusinessAccess access;

    private final AlertRepository alertRepository;

    public AlertQueryServiceImpl(AlertRepository alertRepository, BusinessAccess access) {
        this.access = access;
        this.alertRepository = alertRepository;
    }

    @Override
    public Optional<Alert> handle(GetAlertByIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.ALERT_READ);

        return alertRepository.findByIdAndBusinessId(query.alertId(), actor.businessId());
    }

    @Override
    public List<Alert> handle(GetAlertsQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.ALERT_READ);
        BusinessAccess.verifyClaim(actor, query.businessId());

        return alertRepository.findByFilters(
                actor.businessId(), query.productId(),
                query.lotId(), query.resolved());
    }
}
