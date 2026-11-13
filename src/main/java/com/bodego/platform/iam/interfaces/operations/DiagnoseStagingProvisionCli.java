package com.bodego.platform.iam.interfaces.operations;

import java.io.PrintStream;
import java.sql.*;
import java.util.*;

/** Independent SELECT-only diagnostic. No Spring, Flyway or provisioning service is invoked. */
public final class DiagnoseStagingProvisionCli {
    private DiagnoseStagingProvisionCli() {}
    @FunctionalInterface interface Connector {Connection open(String url,Properties properties) throws SQLException;}
    static StagingJdbcSupport.Target target(String url) {
        var target=StagingJdbcSupport.parse(url);
        if(!"bodego_staging".equals(target.database())) throw new IllegalArgumentException("DIAGNOSE_TARGET_REJECTED");
        return target;
    }
    static String approval(StagingJdbcSupport.Target target) {
        return "AUTHORIZE READ ONLY DIAGNOSIS "+target.url()+" public";
    }
    static void authorize(StagingJdbcSupport.Target target,String answer) {
        if(!approval(target).equals(answer)) throw new IllegalArgumentException("DIAGNOSE_APPROVAL_REJECTED");
    }
    static final String OBJECTS_SQL="SELECT to_regclass('public.businesses') IS NOT NULL AS businesses_exists,"
            +"to_regclass('public.users') IS NOT NULL AS users_exists,"
            +"to_regclass('public.flyway_schema_history') IS NOT NULL AS history_exists,"
            +"has_schema_privilege(current_user,'public','USAGE') AS schema_usage,"
            +"current_user='bodego_runtime' AS role_is_runtime,"
            +"has_function_privilege(current_user,'pg_catalog.pg_advisory_xact_lock(bigint)','EXECUTE') AS advisory_lock_execute";
    static final String PERMISSIONS_SQL="SELECT "
            +"has_table_privilege(current_user,to_regclass('public.businesses'),'SELECT') AS businesses_select,"
            +"has_table_privilege(current_user,to_regclass('public.businesses'),'INSERT') AS businesses_insert,"
            +"has_table_privilege(current_user,to_regclass('public.businesses'),'UPDATE') AS businesses_update,"
            +"has_table_privilege(current_user,to_regclass('public.users'),'SELECT') AS users_select,"
            +"has_table_privilege(current_user,to_regclass('public.users'),'INSERT') AS users_insert,"
            +"has_table_privilege(current_user,to_regclass('public.flyway_schema_history'),'SELECT') AS history_select";
    static final String COLUMNS_SQL="SELECT c.relname,a.attname,t.typname,a.attnotnull,a.attidentity,a.atttypmod "
            +"FROM pg_catalog.pg_attribute a JOIN pg_catalog.pg_class c ON c.oid=a.attrelid "
            +"JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace JOIN pg_catalog.pg_type t ON t.oid=a.atttypid "
            +"WHERE n.nspname='public' AND c.relname IN ('businesses','users') AND a.attnum>0 AND NOT a.attisdropped";
    static final String SEQUENCES_SQL="SELECT "
            +"has_sequence_privilege(current_user,pg_get_serial_sequence('public.businesses','id'),'USAGE') AS business_identity_usage,"
            +"has_sequence_privilege(current_user,pg_get_serial_sequence('public.users','id'),'USAGE') AS user_identity_usage";
    static final String HISTORY_SQL="SELECT version,type,success FROM public.flyway_schema_history ORDER BY installed_rank";
    static final String BUSINESS_COUNT_SQL="SELECT count(*) FROM public.businesses";
    static final String USER_COUNT_SQL="SELECT count(*),count(*) FILTER (WHERE user_role='ADMIN'),"
            +"count(*) FILTER (WHERE user_role='ADMIN' AND active) FROM public.users";
    record Column(String type,int length,boolean identity) {}
    static Map<String,Column> expectedColumns() {
        var expected=new LinkedHashMap<String,Column>();
        for(String table:List.of("businesses","users")) {
            expected.put(table+".id",new Column("int8",0,true));
            expected.put(table+".active",new Column("bool",0,false));
            expected.put(table+".created_at",new Column("timestamptz",0,false));
            expected.put(table+".updated_at",new Column("timestamptz",0,false));
        }
        expected.put("businesses.name",new Column("varchar",100,false));
        expected.put("businesses.address",new Column("varchar",255,false));
        expected.put("businesses.phone",new Column("varchar",32,false));
        expected.put("users.business_id",new Column("int8",0,false));
        expected.put("users.full_name",new Column("varchar",150,false));
        expected.put("users.email",new Column("varchar",254,false));
        expected.put("users.password_hash",new Column("varchar",512,false));
        expected.put("users.user_role",new Column("varchar",16,false));
        return expected;
    }
    private static ResultSet query(Statement statement,String sql) throws SQLException {
        // Defense in depth: only the fixed SELECTs above, never caller-provided SQL.
        if(!sql.startsWith("SELECT ") || sql.contains(";")) throw new IllegalStateException("DIAGNOSE_QUERY_REJECTED");
        return statement.executeQuery(sql);
    }
    private static boolean check(PrintStream out,String name,boolean value) {
        out.println(name+"="+value);
        if(!value) out.println("FAILED_PRECONDITION="+name);
        return value;
    }
    static String sqlState(Throwable error) {
        return StagingJdbcSupport.sqlState(error);
    }
    static int diagnose(String url,String username,char[] password,Connector connector,PrintStream out) {
        String stage="TARGET";var properties=new Properties();
        try {
            var target=target(url);
            if(username==null || username.isBlank() || password==null || password.length==0) throw new IllegalArgumentException();
            properties=StagingJdbcSupport.tls();
            properties.setProperty("user",username);properties.setProperty("password",new String(password));
            properties.setProperty("options","-c default_transaction_read_only=on -c statement_timeout=10000 -c idle_in_transaction_session_timeout=30000");
            stage="CONNECT";
            int result;
            try(var connection=connector.open(target.url(),properties)) {
                connection.setReadOnly(true);connection.setAutoCommit(false);
                try {
                    stage="IDENTITY";var identity=StagingJdbcSupport.identity(connection);
                    boolean identityOk=check(out,"DATABASE_MATCH",target.database().equals(identity.database()));
                    identityOk=check(out,"SCHEMA_MATCH","public".equals(identity.schema())) && identityOk;
                    identityOk=check(out,"PORT_MATCH",target.port()==identity.port()) && identityOk;
                    identityOk=check(out,"POSTGRESQL_18",identity.version()>=180000 && identity.version()<190000) && identityOk;
                    identityOk=check(out,"ROLE_MATCH",username.equals(identity.role())) && identityOk;
                    identityOk=check(out,"TLS",identity.tls()) && identityOk;
                    identityOk=check(out,"READ_ONLY",identity.readOnly()) && identityOk;
                    if(!identityOk) {out.println("DIAGNOSIS_COMPLETE=false");out.println("PROVISION_EXECUTED=false");return 2;}
                    try(var statement=connection.createStatement()) {
                        statement.setQueryTimeout(10);
                        stage="OBJECTS";var flags=new HashMap<String,Boolean>();boolean ready=true;
                        try(var rows=query(statement,OBJECTS_SQL)) {
                            if(!rows.next()) throw new SQLException();
                            for(String key:List.of("businesses_exists","users_exists","history_exists","schema_usage","advisory_lock_execute")) {
                                boolean value=rows.getBoolean(key);flags.put(key,value);ready=check(out,key.toUpperCase(Locale.ROOT),value) && ready;
                            }
                            out.println("ROLE_IS_RUNTIME="+rows.getBoolean("role_is_runtime"));
                        }
                        stage="PERMISSIONS";
                        try(var rows=query(statement,PERMISSIONS_SQL)) {
                            if(!rows.next()) throw new SQLException();
                            for(String key:List.of("businesses_select","businesses_insert","businesses_update","users_select","users_insert","history_select")) {
                                boolean value=rows.getBoolean(key);flags.put(key,value);ready=check(out,key.toUpperCase(Locale.ROOT),value) && ready;
                            }
                        }
                        stage="COLUMNS";var remaining=new HashMap<>(expectedColumns());boolean columns=true;
                        try(var rows=query(statement,COLUMNS_SQL)) {
                            while(rows.next()) {
                                String column=rows.getString(1)+"."+rows.getString(2);
                                Column expected=remaining.remove(column);
                                if(expected!=null) {
                                    boolean compatible=expected.type().equals(rows.getString(3)) && rows.getBoolean(4)
                                            && (!expected.identity() || Set.of("a","d").contains(rows.getString(5)))
                                            && (expected.length()==0 || rows.getInt(6)<0 || rows.getInt(6)-4>=expected.length());
                                    if(!compatible) out.println("INCOMPATIBLE_COLUMN="+column);
                                    columns=compatible && columns;
                                }
                            }
                        }
                        remaining.keySet().stream().sorted().forEach(column->out.println("MISSING_COLUMN="+column));
                        columns=columns && remaining.isEmpty();ready=check(out,"COLUMNS_COMPATIBLE",columns) && ready;
                        stage="SEQUENCES";
                        if(columns) try(var rows=query(statement,SEQUENCES_SQL)) {
                            if(!rows.next()) throw new SQLException();
                            ready=check(out,"BUSINESS_IDENTITY_USAGE",rows.getBoolean("business_identity_usage")) && ready;
                            ready=check(out,"USER_IDENTITY_USAGE",rows.getBoolean("user_identity_usage")) && ready;
                        } else out.println("IDENTITY_USAGE_CHECKED=false");
                        stage="HISTORY";
                        if(flags.get("history_exists") && flags.get("history_select")) {
                            var versions=new ArrayList<String>();long entries=0,failed=0,baselines=0;
                            try(var rows=query(statement,HISTORY_SQL)) {
                                while(rows.next()) {
                                    entries++;if(!rows.getBoolean(3)) failed++;
                                    if("BASELINE".equals(rows.getString(2))) baselines++;
                                    if(rows.getBoolean(3) && "SQL".equals(rows.getString(2))) versions.add(rows.getString(1));
                                }
                            }
                            out.println("HISTORY_ENTRY_COUNT="+entries);out.println("HISTORY_FAILED_COUNT="+failed);out.println("HISTORY_BASELINE_COUNT="+baselines);
                            ready=check(out,"HISTORY_SQL_124_MATCH",versions.equals(List.of("1","2","4"))) && ready;
                        } else out.println("HISTORY_CHECKED=false");
                        stage="COUNTS";
                        if(flags.get("businesses_exists") && flags.get("businesses_select")) try(var rows=query(statement,BUSINESS_COUNT_SQL)) {
                            if(!rows.next()) throw new SQLException();long count=rows.getLong(1);
                            out.println("BUSINESSES_COUNT="+count);ready=check(out,"BUSINESSES_EMPTY",count==0) && ready;
                        } else out.println("BUSINESSES_COUNT_CHECKED=false");
                        if(flags.get("users_exists") && flags.get("users_select") && columns) try(var rows=query(statement,USER_COUNT_SQL)) {
                            if(!rows.next()) throw new SQLException();long count=rows.getLong(1);
                            out.println("USERS_COUNT="+count);out.println("ADMIN_COUNT="+rows.getLong(2));out.println("ACTIVE_ADMIN_COUNT="+rows.getLong(3));
                            ready=check(out,"USERS_EMPTY",count==0) && ready;
                        } else out.println("USERS_COUNT_CHECKED=false");
                        result=ready?0:2;stage="CLEANUP";
                    }
                } finally {connection.rollback();}
            }
            out.println("DIAGNOSIS_COMPLETE=true");out.println("DATABASE_PRECONDITIONS_PASS="+(result==0));
            out.println("PERSONAL_INPUTS_CHECKED=false");out.println("PROVISION_EXECUTED=false");
            return result;
        } catch(Exception failure) {
            out.println("DIAGNOSIS_FAILED_STAGE="+stage);out.println("SQLSTATE="+sqlState(failure));
            out.println("DIAGNOSIS_COMPLETE=false");out.println("PROVISION_EXECUTED=false");return 1;
        } finally {properties.clear();}
    }
    public static void main(String[] args) {
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
                .setLevel(ch.qos.logback.classic.Level.OFF);
        java.util.logging.Logger.getLogger("org.postgresql").setLevel(java.util.logging.Level.OFF);
        var console=System.console();
        if(args.length!=0 || console==null) {System.err.println("STAGING_INTERACTIVE_TERMINAL_REQUIRED");System.exit(1);return;}
        char[] password=null;int exit=1;
        try {
            var target=target(console.readLine("Authorized external Render JDBC URL (:5432/bodego_staging, no credentials/options): "));
            console.printf("Type exactly to authorize SELECT-only diagnosis:%n%s%n",approval(target));
            authorize(target,console.readLine("Approval: "));
            String username=console.readLine("Same database role used by failed CLI: ");
            password=console.readPassword("Database password (hidden): ");
            exit=diagnose(target.url(),username,password,DriverManager::getConnection,System.out);
        } catch(Exception failure) {System.err.println("DIAGNOSIS_INPUT_REJECTED SQLSTATE="+sqlState(failure));}
        finally {if(password!=null) Arrays.fill(password,'\0');}
        if(exit!=0) System.exit(exit);
    }
}
