package com.bodego.platform.iam.interfaces.operations;

import com.bodego.platform.iam.domain.exceptions.*;
import java.io.PrintStream;
import org.springframework.transaction.support.TransactionSynchronization;

/** Per-operation observation only. Never changes transaction decisions or prints exceptions/inputs. */
final class ProvisioningDiagnostics {
    enum Stage { TARGET, INPUT_VALIDATION, READ_ONLY_CONNECTION, APPROVAL, WRITE_CONNECTION,
        JPA_INITIALIZATION, TRANSACTION_BEGIN, PRECONDITIONS, CREATE_BUSINESS, CREATE_ADMIN,
        COMMIT, ROLLBACK, CLOSE, COMPLETE }
    enum Outcome { NOT_STARTED, UNKNOWN, ROLLED_BACK, COMMITTED }
    Stage stage=Stage.TARGET;
    Stage failedStage, failureOrigin;
    Outcome outcome=Outcome.NOT_STARTED;
    boolean workStarted, commitEntered;
    Throwable workFailure;
    private String state="UNAVAILABLE",validation="NOT_CLASSIFIED",cleanupState;

    void move(Stage next) {stage=next;}
    void failure(Throwable failure) {if(failedStage==null) capture(stage,failure);}
    private void capture(Stage origin,Throwable failure) {
        failedStage=origin;failureOrigin=origin;state=StagingJdbcSupport.sqlState(failure);
        if(failure instanceof InvalidEmailException) validation="INVALID_EMAIL";
        else if(failure instanceof InvalidPasswordException) validation="INVALID_PASSWORD";
        else if(failure instanceof InvalidIamDataException) validation="INVALID_IAM_DATA";
        if(!validation.equals("NOT_CLASSIFIED")) failedStage=Stage.INPUT_VALIDATION;
    }
    void workFailed(Throwable failure) {workFailure=failure;failure(failure);}
    void transactionFailed(Throwable failure) {
        if(workFailure!=null && workFailure!=failure) {
            // TransactionTemplate replaced a callback error with a rollback/cleanup error.
            outcome=Outcome.UNKNOWN;validation="NOT_CLASSIFIED";capture(Stage.ROLLBACK,failure);
        } else if(workFailure==null) {
            capture(workStarted?Stage.COMMIT:Stage.TRANSACTION_BEGIN,failure);
            if(commitEntered && outcome!=Outcome.COMMITTED) outcome=Outcome.UNKNOWN;
        }
    }
    void completed(int status) {
        outcome=switch(status) {
            case TransactionSynchronization.STATUS_COMMITTED -> Outcome.COMMITTED;
            case TransactionSynchronization.STATUS_ROLLED_BACK -> Outcome.ROLLED_BACK;
            default -> Outcome.UNKNOWN;
        };
    }
    void cleanupFailed(Throwable failure) {cleanupState=StagingJdbcSupport.sqlState(failure);}
    void printFailure(PrintStream out) {
        out.println("STAGING_FAILED_STAGE="+(failedStage==null?stage:failedStage));
        out.println("STAGING_FAILURE_ORIGIN="+(failureOrigin==null?stage:failureOrigin));
        out.println("SQLSTATE="+state);
        if(!validation.equals("NOT_CLASSIFIED")) out.println("STAGING_VALIDATION_CODE="+validation);
        out.println("STAGING_TRANSACTION_OUTCOME="+outcome);
        if(cleanupState!=null) {out.println("STAGING_CLEANUP_FAILED=true");out.println("STAGING_CLEANUP_SQLSTATE="+cleanupState);}
        out.println("STAGING_RETRY_AUTHORIZED=false");
    }
}
