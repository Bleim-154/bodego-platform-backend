package com.bodego.platform.monitoring.domain.model.queries;

public record GetAlertByIdQuery(Long alertId) {

    public GetAlertByIdQuery {
        if (alertId == null || alertId <= 0) {
            throw new IllegalArgumentException("Alert ID must be positive");
        }
    }
}
