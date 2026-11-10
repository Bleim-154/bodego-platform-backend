package com.bodego.platform.monitoring.application.internal.queryservices;

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

    private final AlertRepository alertRepository;

    public AlertQueryServiceImpl(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Override
    public Optional<Alert> handle(GetAlertByIdQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("Alert query is required");
        }
        return alertRepository.findById(query.alertId());
    }

    @Override
    public List<Alert> handle(GetAlertsQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("Alerts query is required");
        }
        return alertRepository.findByFilters(
                query.businessId(), query.productId(),
                query.lotId(), query.resolved());
    }
}
