package com.bodego.platform.monitoring.domain.model.commands;

public record GenerateAlertsCommand(Long businessId) {

    public GenerateAlertsCommand {
        if (businessId == null || businessId <= 0) {
            throw new IllegalArgumentException("Business ID must be positive");
        }
    }
}
