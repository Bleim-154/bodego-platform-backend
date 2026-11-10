package com.bodego.platform.monitoring.interfaces.rest;

import com.bodego.platform.monitoring.domain.model.aggregates.Alert;
import com.bodego.platform.monitoring.domain.model.commands.ResolveAlertCommand;
import com.bodego.platform.monitoring.domain.model.queries.GetAlertByIdQuery;
import com.bodego.platform.monitoring.domain.model.queries.GetAlertsQuery;
import com.bodego.platform.monitoring.domain.model.valueobjects.AlertSeverity;
import com.bodego.platform.monitoring.domain.model.valueobjects.AlertType;
import com.bodego.platform.monitoring.domain.services.AlertCommandService;
import com.bodego.platform.monitoring.domain.services.AlertQueryService;
import com.bodego.platform.monitoring.domain.services.AlertGenerationService;
import com.bodego.platform.monitoring.domain.model.commands.GenerateAlertsCommand;
import com.bodego.platform.monitoring.interfaces.rest.resources.GenerateAlertsResource;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlertsControllerTests {

    private final AlertCommandService commands = mock(
            AlertCommandService.class, withSettings().mockMaker(MockMakers.PROXY));
    private final AlertQueryService queries = mock(
            AlertQueryService.class, withSettings().mockMaker(MockMakers.PROXY));
    private final AlertGenerationService generation = mock(
            AlertGenerationService.class, withSettings().mockMaker(MockMakers.PROXY));
    private final AlertsController controller = new AlertsController(commands, queries, generation);

    @Test
    void generationReturnsOnlyNewAlerts() {
        when(generation.handle(new GenerateAlertsCommand(1L))).thenReturn(List.of());
        var response = controller.generateAlerts(new GenerateAlertsResource(1L));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
        verify(generation).handle(new GenerateAlertsCommand(1L));
    }

    @Test
    void missingAlertReturns404ForQueryAndResolution() {
        when(queries.handle(new GetAlertByIdQuery(4L))).thenReturn(Optional.empty());
        when(commands.handle(new ResolveAlertCommand(4L))).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, controller.getAlertById(4L).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.resolveAlert(4L).getStatusCode());
    }

    @Test
    void listMapsCombinedFiltersAndNullableLotToResources() {
        var alert = new Alert(1L, 2L, null,
                AlertType.LOW_STOCK, AlertSeverity.MEDIUM, "Stock");
        when(queries.handle(new GetAlertsQuery(1L, 2L, null, false)))
                .thenReturn(List.of(alert));

        var response = controller.getAllAlerts(1L, 2L, null, false);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        var resource = response.getBody().getFirst();
        assertEquals(1L, resource.businessId());
        assertEquals(2L, resource.productId());
        assertNull(resource.lotId());
        assertEquals(alert.getGeneratedAt(), resource.generatedAt());
        assertFalse(resource.resolved());
    }

    @Test
    void resolutionReturnsUpdatedResource() {
        var alert = new Alert(1L, 2L, 3L,
                AlertType.EXPIRED, AlertSeverity.HIGH, "Expired");
        alert.resolve();
        when(commands.handle(new ResolveAlertCommand(4L))).thenReturn(Optional.of(alert));

        var response = controller.resolveAlert(4L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().resolved());
        assertEquals(alert.getResolvedAt(), response.getBody().resolvedAt());
    }
}
