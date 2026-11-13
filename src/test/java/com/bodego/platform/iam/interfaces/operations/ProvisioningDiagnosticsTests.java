package com.bodego.platform.iam.interfaces.operations;

import com.bodego.platform.iam.domain.exceptions.*;
import org.junit.jupiter.api.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.io.*;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Spring transaction lifecycle, entirely simulated resources. No SQL or provisioning work. */
class ProvisioningDiagnosticsTests {
    static ch.qos.logback.classic.Logger transactionLogger;
    static ch.qos.logback.classic.Level previousLevel;
    @BeforeAll static void useSameTransactionLoggingPolicyAsCli() {
        transactionLogger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(TransactionTemplate.class);
        previousLevel=transactionLogger.getLevel();transactionLogger.setLevel(ch.qos.logback.classic.Level.OFF);
    }
    @AfterAll static void restoreLogging() {transactionLogger.setLevel(previousLevel);}
    static final String SENSITIVE="fixture-private@example.invalid password=fixture-private hash=fixture-private";
    static final class Tx implements SmartTransactionObject {
        boolean rollbackOnly;
        @Override public boolean isRollbackOnly() {return rollbackOnly;}
        @Override public void flush() {}
    }
    static final class Manager extends AbstractPlatformTransactionManager {
        RuntimeException beginFailure,commitFailure,rollbackFailure;
        boolean globalRollbackOnly,simulateServerCommitBeforeFailure;
        boolean serverCommitted;
        final List<String> events=new ArrayList<>();
        @Override protected Object doGetTransaction() {return new Tx();}
        @Override protected void doBegin(Object transaction,TransactionDefinition definition) {
            events.add("begin");if(beginFailure!=null) throw beginFailure;
            ((Tx)transaction).rollbackOnly=globalRollbackOnly;
        }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            events.add("commit");serverCommitted=commitFailure==null || simulateServerCommitBeforeFailure;
            if(commitFailure!=null) throw commitFailure;
        }
        @Override protected void doRollback(DefaultTransactionStatus status) {
            events.add("rollback");if(rollbackFailure!=null) throw rollbackFailure;
        }
    }
    @AfterEach void noLeakedSynchronization() {
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }
    String report(ProvisioningDiagnostics diagnostics) {
        var bytes=new ByteArrayOutputStream();diagnostics.printFailure(new PrintStream(bytes,true,StandardCharsets.UTF_8));
        String text=bytes.toString(StandardCharsets.UTF_8);
        assertFalse(text.contains(SENSITIVE));assertFalse(text.contains("fixture-private"));
        assertFalse(text.contains("SQLException"));assertFalse(text.contains("\tat "));
        assertTrue(text.contains("STAGING_RETRY_AUTHORIZED=false"));return text;
    }
    ProvisionStagingBusinessAdminCli.Result success() {return new ProvisionStagingBusinessAdminCli.Result(1L,1L);}
    @Test void connectionFailureIsDistinguishedWithoutInitializingJpaOrRunningWork() {
        var diagnostics=new ProvisioningDiagnostics();
        var context=mock(ConfigurableApplicationContext.class);
        assertThrows(SQLException.class,()->ProvisionStagingBusinessAdminCli.withRuntime(()->{
            diagnostics.move(ProvisioningDiagnostics.Stage.WRITE_CONNECTION);
            throw new SQLException(SENSITIVE,"08001");
        },ignored->{throw new AssertionError("Work must not run");},diagnostics));
        String output=report(diagnostics);assertTrue(output.contains("STAGING_FAILED_STAGE=WRITE_CONNECTION"));
        assertTrue(output.contains("SQLSTATE=08001"));assertTrue(output.contains("STAGING_TRANSACTION_OUTCOME=NOT_STARTED"));
        verifyNoInteractions(context);
    }
    @Test void jpaInitializationFailureIsSanitizedWithoutRunningBusinessWork() {
        var diagnostics=new ProvisioningDiagnostics();
        assertThrows(IllegalStateException.class,()->ProvisionStagingBusinessAdminCli.withRuntime(()->{
            diagnostics.move(ProvisioningDiagnostics.Stage.JPA_INITIALIZATION);
            throw new IllegalStateException(SENSITIVE);
        },ignored->{throw new AssertionError("Work must not run");},diagnostics));
        assertTrue(report(diagnostics).contains("STAGING_FAILED_STAGE=JPA_INITIALIZATION"));
    }
    @Test void preconditionFailureKeepsStageAndRollbackWithoutChangingException() {
        var manager=new Manager();var diagnostics=new ProvisioningDiagnostics();
        var error=new IllegalStateException(SENSITIVE,new SQLException(SENSITIVE,"42501"));
        assertSame(error,assertThrows(IllegalStateException.class,()->ProvisionStagingBusinessAdminCli.observedTransaction(manager,()->{
            diagnostics.move(ProvisioningDiagnostics.Stage.PRECONDITIONS);throw error;
        },diagnostics)));
        assertEquals(List.of("begin","rollback"),manager.events);
        assertTrue(report(diagnostics).contains("STAGING_FAILED_STAGE=PRECONDITIONS"));
        assertTrue(report(diagnostics).contains("SQLSTATE=42501"));assertEquals(ProvisioningDiagnostics.Outcome.ROLLED_BACK,diagnostics.outcome);
    }
    @Test void businessValidationIsIdentifiedSeparatelyFromPersistence() {
        var diagnostics=new ProvisioningDiagnostics();
        assertThrows(InvalidIamDataException.class,()->ProvisionStagingBusinessAdminCli.observedTransaction(new Manager(),()->{
            diagnostics.move(ProvisioningDiagnostics.Stage.CREATE_BUSINESS);throw new InvalidIamDataException(SENSITIVE);
        },diagnostics));
        String output=report(diagnostics);assertTrue(output.contains("STAGING_FAILED_STAGE=INPUT_VALIDATION"));
        assertTrue(output.contains("STAGING_FAILURE_ORIGIN=CREATE_BUSINESS"));assertTrue(output.contains("STAGING_VALIDATION_CODE=INVALID_IAM_DATA"));
        assertEquals(ProvisioningDiagnostics.Outcome.ROLLED_BACK,diagnostics.outcome);
    }
    @Test void adminEmailAndPasswordValidationNeverPrintInputs() {
        for(RuntimeException error:List.of(new InvalidEmailException(),new InvalidPasswordException(SENSITIVE))) {
            var diagnostics=new ProvisioningDiagnostics();
            assertSame(error,assertThrows(RuntimeException.class,()->ProvisionStagingBusinessAdminCli.observedTransaction(new Manager(),()->{
                diagnostics.move(ProvisioningDiagnostics.Stage.CREATE_ADMIN);throw error;
            },diagnostics)));
            String output=report(diagnostics);assertTrue(output.contains("STAGING_FAILED_STAGE=INPUT_VALIDATION"));
            assertTrue(output.contains("STAGING_FAILURE_ORIGIN=CREATE_ADMIN"));
            assertTrue(output.contains(error instanceof InvalidEmailException?"INVALID_EMAIL":"INVALID_PASSWORD"));
            assertEquals(ProvisioningDiagnostics.Outcome.ROLLED_BACK,diagnostics.outcome);
        }
    }
    @Test void businessAndAdminPersistenceErrorsKeepTheirOwnStageAndSqlstate() {
        for(var stage:List.of(ProvisioningDiagnostics.Stage.CREATE_BUSINESS,ProvisioningDiagnostics.Stage.CREATE_ADMIN)) {
            var diagnostics=new ProvisioningDiagnostics();
            var error=new DataIntegrityViolationException(SENSITIVE,new SQLException(SENSITIVE,"23514"));
            assertSame(error,assertThrows(DataIntegrityViolationException.class,()->ProvisionStagingBusinessAdminCli.observedTransaction(new Manager(),()->{
                diagnostics.move(stage);throw error;
            },diagnostics)));
            assertTrue(report(diagnostics).contains("STAGING_FAILED_STAGE="+stage));
            assertTrue(report(diagnostics).contains("SQLSTATE=23514"));
            assertEquals(ProvisioningDiagnostics.Outcome.ROLLED_BACK,diagnostics.outcome);
        }
    }
    @Test void failureBeforeTransactionCallbackIsNotMistakenForBusinessCreation() {
        var manager=new Manager();manager.beginFailure=new CannotCreateTransactionException(SENSITIVE,new SQLException(SENSITIVE,"08006"));
        var diagnostics=new ProvisioningDiagnostics();
        assertThrows(CannotCreateTransactionException.class,()->ProvisionStagingBusinessAdminCli.observedTransaction(manager,()->{
            throw new AssertionError("Callback must not run");
        },diagnostics));
        assertFalse(diagnostics.workStarted);assertTrue(report(diagnostics).contains("STAGING_FAILED_STAGE=TRANSACTION_BEGIN"));
        assertEquals(ProvisioningDiagnostics.Outcome.NOT_STARTED,diagnostics.outcome);
    }
    @Test void lostCommitReplyIsAlwaysUnknownEvenIfServerActuallyCommitted() {
        for(boolean committed:List.of(false,true)) {
            var manager=new Manager();manager.simulateServerCommitBeforeFailure=committed;
            manager.commitFailure=new TransactionSystemException(SENSITIVE,new SQLException(SENSITIVE,"08006"));
            var diagnostics=new ProvisioningDiagnostics();
            assertThrows(TransactionSystemException.class,()->ProvisionStagingBusinessAdminCli.observedTransaction(manager,this::success,diagnostics));
            assertEquals(committed,manager.serverCommitted);assertTrue(report(diagnostics).contains("STAGING_FAILED_STAGE=COMMIT"));
            assertTrue(report(diagnostics).contains("SQLSTATE=08006"));assertEquals(ProvisioningDiagnostics.Outcome.UNKNOWN,diagnostics.outcome);
            assertFalse(report(diagnostics).contains("STAGING_TRANSACTION_OUTCOME=ROLLED_BACK"));
        }
    }
    @Test void rollbackOnlyCompletionIsKnownRollbackNotSuccessfulCommit() {
        var manager=new Manager();manager.globalRollbackOnly=true;var diagnostics=new ProvisioningDiagnostics();
        assertThrows(UnexpectedRollbackException.class,()->ProvisionStagingBusinessAdminCli.observedTransaction(manager,this::success,diagnostics));
        assertFalse(manager.serverCommitted);assertFalse(diagnostics.commitEntered);
        assertEquals(ProvisioningDiagnostics.Outcome.ROLLED_BACK,diagnostics.outcome);
        assertTrue(report(diagnostics).contains("STAGING_FAILED_STAGE=COMMIT"));
    }
    @Test void rollbackFailureOverridesValidationFailureAndRemainsUnknown() {
        var manager=new Manager();manager.rollbackFailure=new TransactionSystemException(SENSITIVE,new SQLException(SENSITIVE,"08006"));
        var diagnostics=new ProvisioningDiagnostics();
        assertThrows(TransactionSystemException.class,()->ProvisionStagingBusinessAdminCli.observedTransaction(manager,()->{
            diagnostics.move(ProvisioningDiagnostics.Stage.CREATE_ADMIN);throw new InvalidPasswordException(SENSITIVE);
        },diagnostics));
        assertEquals(ProvisioningDiagnostics.Outcome.UNKNOWN,diagnostics.outcome);
        assertTrue(report(diagnostics).contains("STAGING_FAILED_STAGE=ROLLBACK"));
        assertTrue(report(diagnostics).contains("SQLSTATE=08006"));
    }
    @Test void closeFailureAfterCommitDoesNotClaimRollbackOrPermitRetry() throws Exception {
        var manager=new Manager();var diagnostics=new ProvisioningDiagnostics();var context=mock(ConfigurableApplicationContext.class);
        doThrow(new IllegalStateException(SENSITIVE,new SQLException(SENSITIVE,"08006"))).when(context).close();
        assertThrows(IllegalStateException.class,()->ProvisionStagingBusinessAdminCli.withRuntime(()->context,
                ignored->ProvisionStagingBusinessAdminCli.observedTransaction(manager,this::success,diagnostics),diagnostics));
        assertTrue(manager.serverCommitted);assertEquals(ProvisioningDiagnostics.Outcome.COMMITTED,diagnostics.outcome);
        assertTrue(report(diagnostics).contains("STAGING_FAILED_STAGE=CLOSE"));
        assertTrue(report(diagnostics).contains("STAGING_TRANSACTION_OUTCOME=COMMITTED"));verify(context).close();
    }
    @Test void successIsOnlyCompleteAfterCommitAndContextClose() throws Exception {
        var manager=new Manager();var diagnostics=new ProvisioningDiagnostics();var context=mock(ConfigurableApplicationContext.class);
        doAnswer(call->{assertEquals(ProvisioningDiagnostics.Stage.CLOSE,diagnostics.stage);
            assertEquals(ProvisioningDiagnostics.Outcome.COMMITTED,diagnostics.outcome);manager.events.add("close");return null;
        }).when(context).close();
        var result=ProvisionStagingBusinessAdminCli.withRuntime(()->context,
                ignored->ProvisionStagingBusinessAdminCli.observedTransaction(manager,this::success,diagnostics),diagnostics);
        assertEquals(1L,result.businessId());assertEquals(List.of("begin","commit","close"),manager.events);
        assertEquals(ProvisioningDiagnostics.Stage.COMPLETE,diagnostics.stage);assertNull(diagnostics.failedStage);
    }
    @Test void secondaryCleanupAndInvalidSqlstatesRemainRedacted() {
        var diagnostics=new ProvisioningDiagnostics();diagnostics.move(ProvisioningDiagnostics.Stage.JPA_INITIALIZATION);
        diagnostics.failure(new IllegalStateException(SENSITIVE));
        diagnostics.cleanupFailed(new SQLException(SENSITIVE,"08006"));
        assertTrue(report(diagnostics).contains("STAGING_CLEANUP_FAILED=true"));
        assertTrue(report(diagnostics).contains("STAGING_CLEANUP_SQLSTATE=08006"));
        var unsafe=new ProvisioningDiagnostics();unsafe.failure(new SQLException(SENSITIVE,"42501\n"+SENSITIVE));
        assertTrue(report(unsafe).contains("SQLSTATE=UNAVAILABLE"));
    }
}
