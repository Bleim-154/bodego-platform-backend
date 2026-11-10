package com.bodego.platform.monitoring.domain.model.aggregates;

import com.bodego.platform.monitoring.domain.model.commands.ResolveAlertCommand;
import com.bodego.platform.monitoring.domain.model.queries.GetAlertByIdQuery;
import com.bodego.platform.monitoring.domain.model.queries.GetAlertsQuery;
import com.bodego.platform.monitoring.domain.model.valueobjects.AlertSeverity;
import com.bodego.platform.monitoring.domain.model.valueobjects.AlertType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AlertTests {

    @Test
    void createsActiveAlertWithServerGenerationTime() {
        var before = Instant.now();
        var alert = new Alert(1L, 2L, null,
                AlertType.LOW_STOCK, AlertSeverity.MEDIUM, " Low stock ");

        assertFalse(alert.getResolved());
        assertNull(alert.getResolvedAt());
        assertNull(alert.getLotId());
        assertEquals("Low stock", alert.getMessage());
        assertFalse(alert.getGeneratedAt().isBefore(before));
        assertFalse(alert.getGeneratedAt().isAfter(Instant.now()));
    }

    @Test
    void resolvesOnceWithoutChangingGenerationOrResolutionTimeOnRetry() {
        var alert = new Alert(1L, 2L, 3L,
                AlertType.EXPIRED, AlertSeverity.HIGH, "Expired lot");
        var generatedAt = alert.getGeneratedAt();
        var before = Instant.now();

        assertSame(alert, alert.resolve());
        var resolvedAt = alert.getResolvedAt();
        assertTrue(alert.getResolved());
        assertFalse(resolvedAt.isBefore(before));
        assertFalse(resolvedAt.isAfter(Instant.now()));
        assertSame(alert, alert.resolve());
        assertEquals(resolvedAt, alert.getResolvedAt());
        assertEquals(generatedAt, alert.getGeneratedAt());
    }

    @Test
    void rejectsInvalidReferencesAndLowStockLot() {
        assertThrows(IllegalArgumentException.class, () -> new Alert(
                null, 2L, null, AlertType.LOW_STOCK, AlertSeverity.LOW, "Stock"));
        assertThrows(IllegalArgumentException.class, () -> new Alert(
                0L, 2L, null, AlertType.LOW_STOCK, AlertSeverity.LOW, "Stock"));
        assertThrows(IllegalArgumentException.class, () -> new Alert(
                1L, -1L, null, AlertType.LOW_STOCK, AlertSeverity.LOW, "Stock"));
        assertThrows(IllegalArgumentException.class, () -> new Alert(
                1L, null, null, AlertType.LOW_STOCK, AlertSeverity.LOW, "Stock"));
        assertThrows(IllegalArgumentException.class, () -> new Alert(
                1L, 2L, 0L, AlertType.EXPIRED, AlertSeverity.LOW, "Stock"));
        assertThrows(IllegalArgumentException.class, () -> new Alert(
                1L, 2L, 3L, AlertType.LOW_STOCK, AlertSeverity.LOW, "Stock"));
    }

    @Test
    void rejectsMissingTypeSeverityAndInvalidMessages() {
        assertThrows(IllegalArgumentException.class, () -> new Alert(
                1L, 2L, null, null, AlertSeverity.LOW, "Stock"));
        assertThrows(IllegalArgumentException.class, () -> new Alert(
                1L, 2L, null, AlertType.LOW_STOCK, null, "Stock"));
        for (String message : new String[]{null, " ", "x".repeat(256)}) {
            assertThrows(IllegalArgumentException.class, () -> new Alert(
                    1L, 2L, null, AlertType.LOW_STOCK, AlertSeverity.LOW, message));
        }
        assertEquals(255, new Alert(1L, 2L, null, AlertType.LOW_STOCK,
                AlertSeverity.LOW, "x".repeat(255)).getMessage().length());
    }

    @Test
    void validatesCommandsAndOptionalQueryFilters() {
        assertThrows(IllegalArgumentException.class, () -> new ResolveAlertCommand(null));
        assertThrows(IllegalArgumentException.class, () -> new ResolveAlertCommand(0L));
        assertThrows(IllegalArgumentException.class, () -> new GetAlertByIdQuery(-1L));
        assertThrows(IllegalArgumentException.class, () -> new GetAlertByIdQuery(null));
        assertThrows(IllegalArgumentException.class, () -> new GetAlertsQuery(0L, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new GetAlertsQuery(null, -1L, null, null));
        assertThrows(IllegalArgumentException.class, () -> new GetAlertsQuery(null, null, 0L, null));
        assertDoesNotThrow(() -> new GetAlertsQuery(null, null, null, null));
        assertDoesNotThrow(() -> new GetAlertsQuery(1L, 2L, 3L, false));
    }
}
