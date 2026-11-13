package com.bodego.platform.monitoring.application.internal;

import com.bodego.platform.monitoring.application.internal.commandservices.AuditLogCommandServiceImpl;
import com.bodego.platform.monitoring.application.internal.queryservices.AuditLogQueryServiceImpl;
import com.bodego.platform.monitoring.domain.model.commands.RecordAuditLogCommand;
import com.bodego.platform.monitoring.domain.model.queries.GetAuditLogByIdQuery;
import com.bodego.platform.monitoring.domain.model.queries.GetAuditLogsQuery;
import com.bodego.platform.monitoring.domain.services.AuditLogCommandService;
import com.bodego.platform.monitoring.infrastructure.persistence.jpa.repositories.AuditLogRepository;
import com.bodego.platform.monitoring.interfaces.acl.MonitoringContextFacade;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditLogServicesTests {

    private final AuditLogRepository repository = mock(
            AuditLogRepository.class, withSettings().mockMaker(MockMakers.PROXY));
    private final AuditLogCommandServiceImpl commands = new AuditLogCommandServiceImpl(repository, com.bodego.platform.shared.application.security.TestBusinessAccess.admin());
    private final AuditLogQueryServiceImpl queries = new AuditLogQueryServiceImpl(repository, com.bodego.platform.shared.application.security.TestBusinessAccess.admin());
    private final TestTransactionManager manager = new TestTransactionManager();

    private Object transactionalProxy(Object target) {
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var factory = new ProxyFactory(target);
        factory.addAdvice(interceptor);
        return factory.getProxy();
    }

    private MonitoringContextFacade facade() {
        var commandProxy = (AuditLogCommandService) transactionalProxy(commands);
        return (MonitoringContextFacade) transactionalProxy(new MonitoringContextFacade(commandProxy));
    }

    private RecordAuditLogCommand command() {
        return new RecordAuditLogCommand(1L, null, "CREATED", "Waste", 2L, "Recorded");
    }

    @Test
    void facadeAndCommandServiceRejectCallsOutsideTransaction() {
        assertThrows(IllegalTransactionStateException.class,
                () -> facade().recordAction(1L, null, "CREATED", "Waste", 2L, "Recorded"));
        var proxy = (AuditLogCommandService) transactionalProxy(commands);
        assertThrows(IllegalTransactionStateException.class, () -> proxy.handle(command()));
        verifyNoInteractions(repository);
        assertEquals(0, manager.begins);
    }

    @Test
    void facadeAndServiceJoinCallerTransactionWithoutCommittingEarly() {
        when(repository.save(any())).thenAnswer(invocation -> {
            var log = invocation.getArgument(0);
            ReflectionTestUtils.setField(log, "id", 9L);
            return log;
        });
        var facade = facade();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            assertEquals(9L, facade.recordAction(1L, null, "CREATED", "Waste", 2L, "Recorded"));
            assertEquals(1, manager.begins);
            assertEquals(0, manager.commits);
        });
        assertEquals(1, manager.commits);
        assertEquals(0, manager.rollbacks);
        verify(repository).save(argThat(log -> log.getUserId().equals(99L)
                && log.getBusinessId().equals(1L) && log.getEntityId().equals(2L)
                && log.getAction().equals("CREATED") && log.getOccurredAt() != null));
    }

    @Test
    void callerFailureRollsBackSharedTransactionInsteadOfCommittingAuditSeparately() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var facade = facade();
        assertThrows(IllegalStateException.class, () ->
                new TransactionTemplate(manager).executeWithoutResult(status -> {
                    facade.recordAction(1L, null, "CREATED", "Waste", 2L, "Recorded");
                    throw new IllegalStateException("Operation failed");
                }));
        assertEquals(1, manager.begins);
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
    }

    @Test
    void invalidCommandsDoNotReachPersistence() {
        assertThrows(IllegalArgumentException.class, () -> commands.handle(null));
        assertThrows(IllegalArgumentException.class, () -> commands.handle(
                new RecordAuditLogCommand(0L, null, "CREATED", "Waste", 2L, "Recorded")));
        verifyNoInteractions(repository);
    }

    @Test
    void queriesPreserveCombinedFiltersAndMissingIds() {
        when(repository.findByFilters(1L, "Waste", 2L)).thenReturn(List.of());
        when(repository.findByIdAndBusinessId(9L, 1L)).thenReturn(Optional.empty());
        assertTrue(queries.handle(new GetAuditLogsQuery(1L, " Waste ", 2L)).isEmpty());
        assertTrue(queries.handle(new GetAuditLogByIdQuery(9L)).isEmpty());
        verify(repository).findByFilters(1L, "Waste", 2L);
        verify(repository).findByIdAndBusinessId(9L, 1L);
    }

    @Test
    void nullQueriesFailBeforePersistence() {
        assertThrows(IllegalArgumentException.class, () -> queries.handle((GetAuditLogsQuery) null));
        assertThrows(IllegalArgumentException.class, () -> queries.handle((GetAuditLogByIdQuery) null));
        verifyNoInteractions(repository);
    }

    // Exercises Spring propagation and commit timing only; no database resource is enlisted.
    private static class TestTransactionManager extends AbstractPlatformTransactionManager {
        private boolean active;
        private int begins;
        private int commits;
        private int rollbacks;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return active;
        }

        @Override
        protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
            active = true;
            begins++;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            active = false;
        }
    }
}
