package com.bodego.platform.iam.interfaces.operations;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;

/** Explicit NEW staging installation only; never a startup component or a baseline/adoption tool. */
public final class MigrateNewStagingCli {
    private MigrateNewStagingCli() {}
    static void empty(Connection connection) throws SQLException {
        try(var statement=connection.createStatement();var rows=statement.executeQuery(
                "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace "
                +"WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','S','f')")) {
            if(!rows.next() || rows.getLong(1)!=0) throw new SQLException("STAGING_SCHEMA_NOT_EMPTY");
        }
    }
    static void migrate(ProvisionStagingBusinessAdminCli.Target target,String username,char[] password) throws SQLException {
        var flyway=Flyway.configure()
                .dataSource(target.url()+"?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&connectTimeout=5&socketTimeout=30",
                        username,new String(password))
                .defaultSchema("public").schemas("public").createSchemas(false)
                .locations("classpath:db/migration").target("4")
                .baselineOnMigrate(false).cleanDisabled(true).outOfOrder(false).validateOnMigrate(true).load();
        try(var connection=flyway.getConfiguration().getDataSource().getConnection()) {
            connection.setAutoCommit(false); connection.setReadOnly(true);
            ProvisionStagingBusinessAdminCli.verify(connection,target,username,true); empty(connection); connection.rollback();
        }
        if(flyway.migrate().migrationsExecuted!=3) throw new IllegalStateException("STAGING_MIGRATION_RESULT_REJECTED");
        flyway.validate();
        if(!Arrays.stream(flyway.info().applied()).map(info->info.getVersion().toString()).toList().equals(List.of("1","2","4")))
            throw new IllegalStateException("STAGING_HISTORY_REJECTED");
    }
    public static void main(String[] args) {
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
                .setLevel(ch.qos.logback.classic.Level.OFF);
        var console=System.console();
        if(args.length!=0 || console==null) {System.err.println("STAGING_INTERACTIVE_TERMINAL_REQUIRED");System.exit(1);return;}
        char[] password=null; String stage="INPUT"; int exit=0;
        try {
            var target=ProvisionStagingBusinessAdminCli.Target.parse(console.readLine("External Render JDBC URL, explicit :5432, no credentials/options: "));
            String username=console.readLine("Migration/owner database user: ");
            if(username==null || username.isBlank()) throw new IllegalArgumentException();
            password=console.readPassword("Database password (hidden): ");
            if(password==null || password.length==0) throw new IllegalArgumentException();
            stage="READ_ONLY_PREFLIGHT"; ProvisionStagingBusinessAdminCli.inspect(target,username,password);
            var properties=ProvisionStagingBusinessAdminCli.tls();
            properties.setProperty("user",username);properties.setProperty("password",new String(password));
            properties.setProperty("options","-c default_transaction_read_only=on");
            try(var connection=DriverManager.getConnection(target.url(),properties)) {empty(connection);}
            finally {properties.clear();}
            String approval="AUTHORIZE NEW STAGING MIGRATIONS V1 V2 V4 "+target.url()+" public";
            console.printf("Verified TLS destination: %s; schema public; user %s%nType exactly after separate authorization:%n%s%n",target.url(),username,approval);
            if(!approval.equals(console.readLine("Approval (Ctrl+C stops without writes): ")))
                throw new IllegalArgumentException();
            stage="MIGRATE"; migrate(target,username,password);
            console.printf("New staging installation verified: V1, V2, V4. V3 excluded. No accounts created.%n");
        } catch(Exception failure) {
            System.err.println("STAGING_FAILED_"+stage+". Stop; inspect history read-only before retry. No repair, baseline or automatic retry.");exit=1;
        } finally {if(password!=null) Arrays.fill(password,'\0');}
        if(exit!=0) System.exit(exit);
    }
}
