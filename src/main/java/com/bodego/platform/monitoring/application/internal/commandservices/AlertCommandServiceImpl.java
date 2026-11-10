package com.bodego.platform.monitoring.application.internal.commandservices;

import com.bodego.platform.monitoring.domain.model.aggregates.Alert;
import com.bodego.platform.monitoring.domain.model.commands.ResolveAlertCommand;
import com.bodego.platform.monitoring.domain.services.AlertCommandService;
import com.bodego.platform.monitoring.infrastructure.persistence.jpa.repositories.AlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AlertCommandServiceImpl implements AlertCommandService {

    private final AlertRepository alertRepository;

    public AlertCommandServiceImpl(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Override
    @Transactional
    public Optional<Alert> handle(ResolveAlertCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Resolve alert command is required");
        }

        return alertRepository.findWithLockById(command.alertId())
                .map(alert -> {
                    if (Boolean.TRUE.equals(alert.getResolved())) {
                        return alert;
                    }
                    alert.resolve();
                    return alertRepository.save(alert);
                });
    }
}
