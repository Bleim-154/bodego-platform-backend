package com.bodego.platform.monitoring.application.internal;

import com.bodego.platform.monitoring.application.internal.commandservices.AlertCommandServiceImpl;
import com.bodego.platform.monitoring.application.internal.queryservices.AlertQueryServiceImpl;
import com.bodego.platform.monitoring.domain.model.aggregates.Alert;
import com.bodego.platform.monitoring.domain.model.commands.ResolveAlertCommand;
import com.bodego.platform.monitoring.domain.model.queries.GetAlertByIdQuery;
import com.bodego.platform.monitoring.domain.model.queries.GetAlertsQuery;
import com.bodego.platform.monitoring.domain.model.valueobjects.AlertSeverity;
import com.bodego.platform.monitoring.domain.model.valueobjects.AlertType;
import com.bodego.platform.monitoring.infrastructure.persistence.jpa.repositories.AlertRepository;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlertServicesTests {

    private final AlertRepository repository = mock(
            AlertRepository.class, withSettings().mockMaker(MockMakers.PROXY));
    private final AlertCommandServiceImpl commandService = new AlertCommandServiceImpl(repository);
    private final AlertQueryServiceImpl queryService = new AlertQueryServiceImpl(repository);

    @Test
    void resolutionUsesLockedLookupAndSavesOnlyFirstTransition() {
        var alert = new Alert(1L, 2L, null,
                AlertType.LOW_STOCK, AlertSeverity.MEDIUM, "Stock");
        when(repository.findWithLockById(4L)).thenReturn(Optional.of(alert));
        when(repository.save(alert)).thenReturn(alert);

        assertSame(alert, commandService.handle(new ResolveAlertCommand(4L)).orElseThrow());
        var resolvedAt = alert.getResolvedAt();
        assertSame(alert, commandService.handle(new ResolveAlertCommand(4L)).orElseThrow());

        assertEquals(resolvedAt, alert.getResolvedAt());
        verify(repository, times(2)).findWithLockById(4L);
        verify(repository, times(1)).save(alert);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void missingAlertDoesNotWrite() {
        when(repository.findWithLockById(4L)).thenReturn(Optional.empty());
        assertTrue(commandService.handle(new ResolveAlertCommand(4L)).isEmpty());
        verify(repository).findWithLockById(4L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void queryPreservesAllCombinedFiltersAndFalseResolutionState() {
        when(repository.findByFilters(1L, 2L, 3L, false)).thenReturn(List.of());
        assertTrue(queryService.handle(new GetAlertsQuery(1L, 2L, 3L, false)).isEmpty());
        verify(repository).findByFilters(1L, 2L, 3L, false);
    }

    @Test
    void unfilteredQueryAndMissingIdReturnRepositoryResults() {
        when(repository.findByFilters(null, null, null, null)).thenReturn(List.of());
        when(repository.findById(4L)).thenReturn(Optional.empty());
        assertTrue(queryService.handle(new GetAlertsQuery(null, null, null, null)).isEmpty());
        assertTrue(queryService.handle(new GetAlertByIdQuery(4L)).isEmpty());
    }

    @Test
    void nullCommandsAndQueriesDoNotAccessPersistence() {
        assertThrows(IllegalArgumentException.class, () -> commandService.handle(null));
        assertThrows(IllegalArgumentException.class, () -> queryService.handle((GetAlertByIdQuery) null));
        assertThrows(IllegalArgumentException.class, () -> queryService.handle((GetAlertsQuery) null));
        verifyNoInteractions(repository);
    }
}
