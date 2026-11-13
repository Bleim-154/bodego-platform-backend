package com.bodego.platform.iam.interfaces.operations;

import com.bodego.platform.iam.domain.model.commands.*;
import com.bodego.platform.iam.domain.services.*;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.function.*;

/** Standalone, interactive, NEW staging database only. Not discovered by Spring application scanning. */
public final class ProvisionStagingBusinessAdminCli {
    private ProvisionStagingBusinessAdminCli() {}
    record Target(String url,String host,int port,String database) {
        static Target parse(String url) {
            var target=StagingJdbcSupport.parse(url);
            return new Target(target.url(),target.host(),target.port(),target.database());
        }
        String approval() {return "AUTHORIZE NEW STAGING BUSINESS AND ADMIN "+url+" public";}
        void authorize(String answer) {
            if(!approval().equals(answer)) throw new IllegalArgumentException("STAGING_APPROVAL_REJECTED");
        }
    }
    static Properties tls() {
        return StagingJdbcSupport.tls();
    }
    static void inspect(Target target,String username,char[] password) throws SQLException {
        var properties=tls(); properties.setProperty("user",username); properties.setProperty("password",new String(password));
        properties.setProperty("options","-c default_transaction_read_only=on -c statement_timeout=10000");
        try(var connection=DriverManager.getConnection(target.url(),properties)) { verify(connection,target,username,true); }
        finally {properties.clear();}
    }
    static void verify(Connection connection,Target target,String username,boolean readOnly) throws SQLException {
        if(!StagingJdbcSupport.identity(connection).matches(target.database(),target.port(),username,readOnly))
            throw new SQLException("STAGING_IDENTITY_REJECTED");
    }
    record Result(Long businessId,Long userId) {}
    static Result provision(org.springframework.context.ApplicationContext context,Target target,String username,
                            String name,String address,String phone,String fullName,String email,char[] password) {
        return provision(context,target,username,name,address,phone,fullName,email,password,new ProvisioningDiagnostics());
    }
    static Result provision(org.springframework.context.ApplicationContext context,Target target,String username,
                            String name,String address,String phone,String fullName,String email,char[] password,
                            ProvisioningDiagnostics diagnostics) {
        diagnostics.move(ProvisioningDiagnostics.Stage.TRANSACTION_BEGIN);
        return observedTransaction(context.getBean(PlatformTransactionManager.class),()->{
            diagnostics.move(ProvisioningDiagnostics.Stage.WRITE_CONNECTION);
            var jdbc=new JdbcTemplate(context.getBean(DataSource.class));
            jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection->{verify(connection,target,username,false);return null;});
            diagnostics.move(ProvisioningDiagnostics.Stage.PRECONDITIONS);
            // Serialize concurrent bootstraps before checking emptiness; both aggregate writes share this transaction.
            jdbc.execute("SET LOCAL lock_timeout='5s'");
            jdbc.execute("SELECT pg_advisory_xact_lock(4778122711760887)");
            if(!jdbc.queryForList("SELECT version FROM public.flyway_schema_history WHERE success AND type='SQL' ORDER BY installed_rank",String.class)
                    .equals(List.of("1","2","4"))
                    || jdbc.queryForObject("SELECT count(*) FROM public.businesses",Long.class)!=0
                    || jdbc.queryForObject("SELECT count(*) FROM public.users",Long.class)!=0)
                throw new IllegalStateException("STAGING_NOT_FRESH_OR_NOT_MIGRATED");
            diagnostics.move(ProvisioningDiagnostics.Stage.CREATE_BUSINESS);
            Long businessId=context.getBean(BusinessCommandService.class).handle(new CreateBusinessCommand(name,address,phone));
            diagnostics.move(ProvisioningDiagnostics.Stage.CREATE_ADMIN);
            try(var command=new ProvisionFirstAdminCommand(businessId,fullName,email,password)) {
                Long userId=context.getBean(UserCommandService.class).handle(command);
                return new Result(businessId,userId);
            }
        },diagnostics);
    }
    static Result observedTransaction(PlatformTransactionManager manager,Supplier<Result> work,ProvisioningDiagnostics diagnostics) {
        diagnostics.move(ProvisioningDiagnostics.Stage.TRANSACTION_BEGIN);
        try {
            return new TransactionTemplate(manager).execute(status->{
                diagnostics.outcome=ProvisioningDiagnostics.Outcome.UNKNOWN;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) {
                        diagnostics.commitEntered=true;diagnostics.move(ProvisioningDiagnostics.Stage.COMMIT);
                    }
                    @Override public void afterCompletion(int completionStatus) {diagnostics.completed(completionStatus);}
                });
                diagnostics.workStarted=true;
                try {
                    var result=work.get();diagnostics.move(ProvisioningDiagnostics.Stage.COMMIT);return result;
                } catch(RuntimeException | Error failure) {diagnostics.workFailed(failure);throw failure;}
            });
        } catch(RuntimeException | Error failure) {diagnostics.transactionFailed(failure);throw failure;}
    }
    @FunctionalInterface interface RuntimeFactory {ConfigurableApplicationContext open() throws Exception;}
    static Result withRuntime(RuntimeFactory factory,Function<ConfigurableApplicationContext,Result> work,
                              ProvisioningDiagnostics diagnostics) throws Exception {
        try {
            Result result;
            try(var context=factory.open()) {
                result=work.apply(context);diagnostics.move(ProvisioningDiagnostics.Stage.CLOSE);
            }
            diagnostics.move(ProvisioningDiagnostics.Stage.COMPLETE);return result;
        } catch(Exception | Error failure) {
            diagnostics.failure(failure);
            if(failure.getSuppressed().length>0) diagnostics.cleanupFailed(failure.getSuppressed()[0]);
            throw failure;
        }
    }
    public static void main(String[] args) {
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
                .setLevel(ch.qos.logback.classic.Level.OFF);
        java.util.logging.Logger.getLogger("org.postgresql").setLevel(java.util.logging.Level.OFF);
        var console=System.console();
        if(args.length!=0 || console==null) {System.err.println("STAGING_INTERACTIVE_TERMINAL_REQUIRED");System.exit(1);return;}
        char[] dbPassword=null,email=null,password=null,repeat=null;
        var diagnostics=new ProvisioningDiagnostics();int exit=0;
        try {
            var target=Target.parse(console.readLine("External Render JDBC URL, explicit :5432, no credentials/options: "));
            diagnostics.move(ProvisioningDiagnostics.Stage.INPUT_VALIDATION);
            String username=console.readLine("Migration/owner database user: ");
            if(username==null || username.isBlank()) throw new IllegalArgumentException();
            dbPassword=console.readPassword("Database password (hidden): ");
            if(dbPassword==null || dbPassword.length==0) throw new IllegalArgumentException();
            diagnostics.move(ProvisioningDiagnostics.Stage.READ_ONLY_CONNECTION);inspect(target,username,dbPassword);
            diagnostics.move(ProvisioningDiagnostics.Stage.INPUT_VALIDATION);
            String name=console.readLine("Approved Business name: "),address=console.readLine("Address: "),phone=console.readLine("Phone: ");
            String fullName=console.readLine("Approved ADMIN full name: ");
            email=console.readPassword("ADMIN email (hidden): ");
            password=console.readPassword("Initial password (hidden): "); repeat=console.readPassword("Repeat password (hidden): ");
            if(email==null || password==null || repeat==null || !Arrays.equals(password,repeat)) throw new IllegalArgumentException();
            diagnostics.move(ProvisioningDiagnostics.Stage.APPROVAL);
            console.printf("Separate operational authorization required. Type exactly:%n%s%n",target.approval());
            target.authorize(console.readLine("Approval (Ctrl+C stops without writes): "));
            diagnostics.move(ProvisioningDiagnostics.Stage.READ_ONLY_CONNECTION);
            inspect(target,username,dbPassword);
            diagnostics.move(ProvisioningDiagnostics.Stage.WRITE_CONNECTION);
            final char[] approvedDbPassword=dbPassword,approvedEmail=email,approvedPassword=password;
            withRuntime(()->ProvisioningRuntime.open(target.url(),username,approvedDbPassword,tls(),diagnostics),
                    context->provision(context,target,username,name,address,phone,fullName,new String(approvedEmail),approvedPassword,diagnostics),diagnostics);
            console.printf("STAGING_COMPLETED_STAGE=COMPLETE%nSTAGING_TRANSACTION_OUTCOME=%s%n",diagnostics.outcome);
        } catch(Throwable failure) {
            diagnostics.failure(failure);diagnostics.printFailure(System.err);exit=1;
        } finally {for(char[] value:new char[][]{dbPassword,email,password,repeat}) if(value!=null) Arrays.fill(value,'\0');}
        if(exit!=0) System.exit(exit);
    }
}
