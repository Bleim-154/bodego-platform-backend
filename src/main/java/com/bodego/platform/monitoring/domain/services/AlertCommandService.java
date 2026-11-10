package com.bodego.platform.monitoring.domain.services;

import com.bodego.platform.monitoring.domain.model.aggregates.Alert;
import com.bodego.platform.monitoring.domain.model.commands.ResolveAlertCommand;

import java.util.Optional;

public interface AlertCommandService {

    Optional<Alert> handle(ResolveAlertCommand command);
}
