package com.bodego.platform.strategicoffers.application.internal.outboundservices.acl;

import com.bodego.platform.monitoring.interfaces.acl.MonitoringContextFacade;
import org.springframework.stereotype.Service;

@Service("strategicOffersExternalMonitoringService")
public class ExternalMonitoringService {

    private final MonitoringContextFacade monitoringContextFacade;

    public ExternalMonitoringService(
            MonitoringContextFacade monitoringContextFacade
    ) {
        this.monitoringContextFacade = monitoringContextFacade;
    }

    public void recordAction(
            Long businessId,
            String action,
            String entityType,
            Long entityId,
            String description
    ) {
        monitoringContextFacade.recordAction(
                businessId, null, action, entityType, entityId, description);
    }
}
