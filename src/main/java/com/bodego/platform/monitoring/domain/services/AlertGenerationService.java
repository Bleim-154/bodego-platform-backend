package com.bodego.platform.monitoring.domain.services;

import com.bodego.platform.monitoring.domain.model.aggregates.Alert;
import com.bodego.platform.monitoring.domain.model.commands.GenerateAlertsCommand;

import java.util.List;

public interface AlertGenerationService {

    List<Alert> handle(GenerateAlertsCommand command);
}
