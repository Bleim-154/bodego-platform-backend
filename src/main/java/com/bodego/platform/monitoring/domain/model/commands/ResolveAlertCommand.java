package com.bodego.platform.monitoring.domain.model.commands;

public record ResolveAlertCommand(Long alertId) {

    public ResolveAlertCommand {
        if (alertId == null || alertId <= 0) {
            throw new IllegalArgumentException("Alert ID must be positive");
        }
    }
}
