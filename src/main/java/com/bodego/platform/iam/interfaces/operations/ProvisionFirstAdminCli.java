package com.bodego.platform.iam.interfaces.operations;

import com.bodego.platform.iam.domain.model.commands.ProvisionFirstAdminCommand;
import com.bodego.platform.iam.domain.model.valueobjects.IamValidation;
import com.bodego.platform.iam.domain.services.UserCommandService;
import java.io.Console;
import java.net.URI;
import java.sql.*;
import java.util.*;

/** Explicit standalone entry point: never a Spring component, runner or HTTP endpoint. */
public final class ProvisionFirstAdminCli {
    private ProvisionFirstAdminCli() {}

    record Target(String jdbcUrl,String host,int port,String database,int sqlPort) {
        static Target parse(String url) {
            if(url==null || !url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException("Explicit PostgreSQL URL required");
            URI uri=URI.create(url.substring(5));
            String database=uri.getPath()==null || uri.getPath().isEmpty()?"":uri.getPath().substring(1);
            if(!Set.of("localhost","127.0.0.1").contains(uri.getHost()) || uri.getPort()<1024 || uri.getPort()>65535
                    || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                    || !database.matches("[A-Za-z][A-Za-z0-9_]*") || Set.of("postgres","template0","template1").contains(database))
                throw new IllegalArgumentException("Explicit local host, port and application database required; no URL credentials/options");
            return new Target(url,uri.getHost(),uri.getPort(),database,uri.getPort());
        }
        Target expectSqlPort(int sqlPort) {
            if(sqlPort<1 || sqlPort>65535) throw new IllegalArgumentException("Explicit SQL server port required");
            return new Target(jdbcUrl,host,port,database,sqlPort);
        }
        String confirmation(Long businessId) { return "AUTHORIZE FIRST ADMIN "+jdbcUrl+"/public SQL_PORT "+sqlPort+" BUSINESS "+businessId; }
        void authorize(Long businessId,String confirmation) {
            IamValidation.positiveId(businessId);
            if(!confirmation(businessId).equals(confirmation)) throw new IllegalArgumentException("Operational authorization did not match");
        }
    }

    static String inspect(Target target,String username,char[] databasePassword) throws SQLException {
        var properties=new Properties();
        properties.setProperty("user",username); properties.setProperty("password",new String(databasePassword));
        properties.setProperty("options","-c default_transaction_read_only=on -c statement_timeout=10000");
        properties.setProperty("connectTimeout","5"); properties.setProperty("socketTimeout","10");
        try(var connection=DriverManager.getConnection(target.jdbcUrl(),properties);var statement=connection.createStatement();
            var result=statement.executeQuery("SELECT current_database(),current_schema(),inet_server_port(),current_setting('server_version_num'),current_setting('data_directory'),inet_server_addr(),current_setting('transaction_read_only')")) {
            if(!result.next() || !target.database().equals(result.getString(1)) || !"public".equals(result.getString(2))
                    || target.sqlPort()!=result.getInt(3) || !"180006".equals(result.getString(4))
                    || result.getString(5)==null || result.getString(5).isBlank() || !"on".equals(result.getString(7)))
                throw new SQLException("Destination identity rejected");
            return target.jdbcUrl()+"/public; PostgreSQL 18.6; SQL_port="+result.getInt(3)
                    +"; SQL_address="+result.getString(6)+"; data_directory="+result.getString(5);
        } finally {properties.clear();}
    }

    public static void main(String[] args) {
        // No application logs in this operation: driver/Hibernate errors can carry row values.
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
                .setLevel(ch.qos.logback.classic.Level.OFF);
        Console console=System.console();
        if(args.length!=0 || console==null) {
            System.err.println("Interactive terminal required; arguments, redirected input and automatic startup are not supported.");
            System.exit(1); return;
        }
        char[] databasePassword=null,password=null,repeat=null,email=null;
        int exitCode=0;
        try {
            var target=Target.parse(console.readLine("Explicit JDBC URL (no default): "));
            target=target.expectSqlPort(Integer.parseInt(console.readLine("Expected SQL server port (internal port for Docker; no default): ")));
            String username=console.readLine("Database user (no default): ");
            if(username==null || username.isBlank()) throw new IllegalArgumentException();
            databasePassword=console.readPassword("Database password (hidden): ");
            if(databasePassword==null || databasePassword.length==0) throw new IllegalArgumentException();
            Long businessId=Long.valueOf(console.readLine("Existing Business ID: "));
            IamValidation.positiveId(businessId);
            String identity=inspect(target,username,databasePassword);
            console.printf("Verified destination: %s%n",identity);
            console.printf("A separate operational approval is required. Type exactly:%n%s%n",target.confirmation(businessId));
            target.authorize(businessId,console.readLine("Approval: "));
            String fullName=console.readLine("Full name: ");
            email=console.readPassword("Email (hidden): ");
            password=console.readPassword("Initial password (hidden): ");
            repeat=console.readPassword("Repeat initial password (hidden): ");
            if(email==null || password==null || repeat==null || !Arrays.equals(password,repeat)) throw new IllegalArgumentException();
            if(!identity.equals(inspect(target,username,databasePassword))) throw new IllegalStateException("Destination changed");
            try(var context=ProvisioningRuntime.open(target.jdbcUrl(),username,databasePassword);
                var command=new ProvisionFirstAdminCommand(businessId,fullName,new String(email),password)) {
                Long id=context.getBean(UserCommandService.class).handle(command);
                console.printf("Provisioning completed. User ID: %d; Business ID: %d. No credentials displayed.%n",id,businessId);
            }
        } catch(Exception exception) {
            System.err.println("Provisioning failed or was rejected. Inspect the authorized target safely before any retry; no credentials displayed.");
            exitCode=1;
        } finally {
            for(char[] value:new char[][]{databasePassword,password,repeat,email}) if(value!=null) Arrays.fill(value,'\0');
        }
        if(exitCode!=0) System.exit(exitCode);
    }
}
