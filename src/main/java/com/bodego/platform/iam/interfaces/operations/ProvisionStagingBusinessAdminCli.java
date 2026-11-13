package com.bodego.platform.iam.interfaces.operations;

import com.bodego.platform.iam.domain.model.commands.*;
import com.bodego.platform.iam.domain.services.*;
import java.net.URI;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;

/** Standalone, interactive, NEW staging database only. Not discovered by Spring application scanning. */
public final class ProvisionStagingBusinessAdminCli {
    private ProvisionStagingBusinessAdminCli() {}
    record Target(String url,String host,int port,String database) {
        static Target parse(String url) {
            try {
                if(url==null || !url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();
                var uri=URI.create(url.substring(5)); String host=uri.getHost();
                String database=uri.getPath()==null?"":uri.getPath().substring(1);
                if(host==null || !host.endsWith(".render.com") || uri.getPort()!=5432
                        || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                        || !database.matches("[A-Za-z][A-Za-z0-9_]*")
                        || Set.of("bodego_db","postgres","template0","template1").contains(database.toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException();
                return new Target(url,host,5432,database);
            } catch(RuntimeException failure) {throw new IllegalArgumentException("STAGING_TARGET_REJECTED");}
        }
        String approval() {return "AUTHORIZE NEW STAGING BUSINESS AND ADMIN "+url+" public";}
        void authorize(String answer) {
            if(!approval().equals(answer)) throw new IllegalArgumentException("STAGING_APPROVAL_REJECTED");
        }
    }
    static Properties tls() {
        var properties=new Properties();
        properties.setProperty("sslmode","verify-full");
        properties.setProperty("sslfactory","org.postgresql.ssl.DefaultJavaSSLFactory");
        properties.setProperty("connectTimeout","5"); properties.setProperty("socketTimeout","30");
        return properties;
    }
    static void inspect(Target target,String username,char[] password) throws SQLException {
        var properties=tls(); properties.setProperty("user",username); properties.setProperty("password",new String(password));
        properties.setProperty("options","-c default_transaction_read_only=on -c statement_timeout=10000");
        try(var connection=DriverManager.getConnection(target.url(),properties)) { verify(connection,target,username,true); }
        finally {properties.clear();}
    }
    static void verify(Connection connection,Target target,String username,boolean readOnly) throws SQLException {
        try(var statement=connection.createStatement();var rows=statement.executeQuery(
                "SELECT current_database(),current_schema(),inet_server_port(),current_setting('server_version_num')::int,current_user,"
                +"current_setting('transaction_read_only'),(SELECT ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid())")) {
            if(!rows.next() || !target.database().equals(rows.getString(1)) || !"public".equals(rows.getString(2))
                    || target.port()!=rows.getInt(3) || rows.getInt(4)<180000 || rows.getInt(4)>=190000
                    || !username.equals(rows.getString(5)) || !rows.getBoolean(7)
                    || (readOnly && !"on".equals(rows.getString(6)))) throw new SQLException("STAGING_IDENTITY_REJECTED");
        }
    }
    record Result(Long businessId,Long userId) {}
    static Result provision(org.springframework.context.ApplicationContext context,Target target,String username,
                            String name,String address,String phone,String fullName,String email,char[] password) {
        return new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).execute(status->{
            var jdbc=new JdbcTemplate(context.getBean(DataSource.class));
            jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection->{verify(connection,target,username,false);return null;});
            // Serialize concurrent bootstraps before checking emptiness; both aggregate writes share this transaction.
            jdbc.execute("SET LOCAL lock_timeout='5s'");
            jdbc.execute("SELECT pg_advisory_xact_lock(4778122711760887)");
            if(!jdbc.queryForList("SELECT version FROM public.flyway_schema_history WHERE success AND type='SQL' ORDER BY installed_rank",String.class)
                    .equals(List.of("1","2","4"))
                    || jdbc.queryForObject("SELECT count(*) FROM public.businesses",Long.class)!=0
                    || jdbc.queryForObject("SELECT count(*) FROM public.users",Long.class)!=0)
                throw new IllegalStateException("STAGING_NOT_FRESH_OR_NOT_MIGRATED");
            Long businessId=context.getBean(BusinessCommandService.class).handle(new CreateBusinessCommand(name,address,phone));
            try(var command=new ProvisionFirstAdminCommand(businessId,fullName,email,password)) {
                Long userId=context.getBean(UserCommandService.class).handle(command);
                return new Result(businessId,userId);
            }
        });
    }
    public static void main(String[] args) {
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
                .setLevel(ch.qos.logback.classic.Level.OFF);
        var console=System.console();
        if(args.length!=0 || console==null) {System.err.println("STAGING_INTERACTIVE_TERMINAL_REQUIRED");System.exit(1);return;}
        char[] dbPassword=null,email=null,password=null,repeat=null;
        String stage="INPUT"; int exit=0;
        try {
            var target=Target.parse(console.readLine("External Render JDBC URL, explicit :5432, no credentials/options: "));
            String username=console.readLine("Migration/owner database user: ");
            if(username==null || username.isBlank()) throw new IllegalArgumentException();
            dbPassword=console.readPassword("Database password (hidden): ");
            if(dbPassword==null || dbPassword.length==0) throw new IllegalArgumentException();
            stage="READ_ONLY_PREFLIGHT"; inspect(target,username,dbPassword);
            console.printf("Verified TLS PostgreSQL 18 destination: %s; schema public; user %s%n",target.url(),username);
            String name=console.readLine("Approved Business name: "),address=console.readLine("Address: "),phone=console.readLine("Phone: ");
            String fullName=console.readLine("Approved ADMIN full name: ");
            email=console.readPassword("ADMIN email (hidden): ");
            password=console.readPassword("Initial password (hidden): "); repeat=console.readPassword("Repeat password (hidden): ");
            if(email==null || password==null || repeat==null || !Arrays.equals(password,repeat)) throw new IllegalArgumentException();
            stage="APPROVAL"; console.printf("Separate operational authorization required. Type exactly:%n%s%n",target.approval());
            target.authorize(console.readLine("Approval (Ctrl+C stops without writes): "));
            stage="PROVISION";
            inspect(target,username,dbPassword);
            try(var context=ProvisioningRuntime.open(target.url(),username,dbPassword,tls())) {
                var result=provision(context,target,username,name,address,phone,fullName,new String(email),password);
                console.printf("Staging provisioning completed. Business ID: %d; User ID: %d. No credentials displayed.%n",result.businessId(),result.userId());
            }
        } catch(Exception failure) {
            System.err.println("STAGING_FAILED_"+stage+". Stop; inspect target read-only before retry. No credentials displayed.");exit=1;
        } finally {for(char[] value:new char[][]{dbPassword,email,password,repeat}) if(value!=null) Arrays.fill(value,'\0');}
        if(exit!=0) System.exit(exit);
    }
}
