package com.bodego.platform.iam.interfaces.operations;

import java.io.PrintStream;
import java.sql.*;
import java.util.*;
import org.postgresql.PGConnection;

/** Standalone operator tool. Default PLAN and CHECK are read-only; CREATE needs separate exact approval. */
public final class CreateStagingRuntimeRoleCli {
    private CreateStagingRuntimeRoleCli() {}
    static final String ROLE="bodego_runtime";
    static final List<String> WRITABLE=List.of("categories","products","lots","correction_requests","alerts","recommendations","offers","businesses","users");
    static final List<String> APPEND=List.of("audit_logs","inventory_movements","physical_counts","stock_adjustments","wastes");
    static List<String> tables() {var all=new ArrayList<>(WRITABLE);all.addAll(APPEND);return all;}
    static String qualified(List<String> names) {return String.join(", ",names.stream().map(name->"public."+name).toList());}
    static List<String> grantStatements() {
        return List.of(
                "GRANT CONNECT ON DATABASE bodego_staging TO bodego_runtime",
                "REVOKE CREATE, TEMPORARY ON DATABASE bodego_staging FROM PUBLIC, bodego_runtime",
                "REVOKE CREATE ON SCHEMA public FROM PUBLIC, bodego_runtime",
                "GRANT USAGE ON SCHEMA public TO bodego_runtime",
                "GRANT SELECT, INSERT, UPDATE ON "+qualified(WRITABLE)+" TO bodego_runtime",
                "GRANT SELECT, INSERT ON "+qualified(APPEND)+" TO bodego_runtime",
                "GRANT UPDATE (updated_at) ON public.physical_counts TO bodego_runtime",
                "GRANT USAGE ON SEQUENCE "+qualified(tables().stream().map(name->name+"_id_seq").toList())+" TO bodego_runtime");
    }
    static String values(List<String> names) {return String.join(",",names.stream().map(name->"('public."+name+"')").toList());}
    static StagingJdbcSupport.Target target(String url) {
        var target=StagingJdbcSupport.parse(url);
        if(!"bodego_staging".equals(target.database())) throw new IllegalArgumentException("RUNTIME_TARGET_REJECTED");
        return target;
    }
    static String approval(StagingJdbcSupport.Target target) {
        return "AUTHORIZE GLOBAL ROLE bodego_runtime IN DEDICATED CLUSTER AND GRANTS "+target.url()+" public POLICY runtime-v1";
    }
    static void passwordPolicy(char[] password) {
        if(password==null || password.length<24 || password.length>128) throw new IllegalArgumentException("RUNTIME_PASSWORD_REJECTED");
        for(char c:password) if(c<33 || c>126) throw new IllegalArgumentException("RUNTIME_PASSWORD_REJECTED");
    }
    @FunctionalInterface interface Connector {Connection open(String url,Properties properties) throws SQLException;}
    static final String PREFLIGHT_SQL="SELECT "
            +"EXISTS (SELECT 1 FROM pg_roles WHERE rolname='bodego_runtime') AS role_exists,"
            +"(SELECT rolcreaterole OR rolsuper FROM pg_roles WHERE rolname=current_user) AS can_create_role,"
            +"pg_has_role(current_user,(SELECT datdba FROM pg_database WHERE datname=current_database()),'USAGE') AS owns_database,"
            +"pg_has_role(current_user,(SELECT nspowner FROM pg_namespace WHERE nspname='public'),'USAGE') AS owns_schema,"
            +"NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' "
            +"AND c.relkind IN ('r','p','S') AND NOT pg_has_role(current_user,c.relowner,'USAGE')) AS owns_objects,"
            +"NOT EXISTS (SELECT 1 FROM pg_database WHERE NOT datistemplate AND datname NOT IN ('bodego_staging','postgres')) AS dedicated_cluster,"
            +"NOT pg_is_in_recovery() AS primary_server,"
            +"current_user<>'bodego_runtime' AS distinct_owner";
    static final String SNAPSHOT_SQL="SELECT (SELECT count(*) FROM public.businesses),"
            +"(SELECT count(*) FROM public.users),(SELECT count(*) FROM public.users WHERE user_role='ADMIN' AND active)";
    static final String HISTORY_SQL="SELECT array_agg(version::text ORDER BY installed_rank) FILTER (WHERE success AND type='SQL') "
            +"=ARRAY['1','2','4'] AND count(*) FILTER (WHERE NOT success)=0 AS history_ok FROM public.flyway_schema_history";
    static final String LOCK_SQL="SELECT pg_advisory_xact_lock(4778122711760888)";
    static final String VERIFY_SQL="SELECT "
            +"EXISTS (SELECT 1 FROM pg_roles WHERE rolname='bodego_runtime' AND rolcanlogin AND NOT rolsuper AND NOT rolcreatedb "
            +"AND NOT rolcreaterole AND NOT rolreplication AND NOT rolbypassrls AND NOT rolinherit) AS role_flags,"
            +"NOT EXISTS (SELECT 1 FROM pg_auth_members WHERE member=(SELECT oid FROM pg_roles WHERE rolname='bodego_runtime')) AS no_memberships,"
            +"NOT EXISTS (SELECT 1 FROM pg_class WHERE relowner=(SELECT oid FROM pg_roles WHERE rolname='bodego_runtime')) "
            +"AND NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspowner=(SELECT oid FROM pg_roles WHERE rolname='bodego_runtime')) "
            +"AND NOT EXISTS (SELECT 1 FROM pg_database WHERE datdba=(SELECT oid FROM pg_roles WHERE rolname='bodego_runtime')) AS no_ownership,"
            +"has_database_privilege('bodego_runtime',current_database(),'CONNECT') AND has_schema_privilege('bodego_runtime','public','USAGE') AS can_connect,"
            +"NOT has_database_privilege('bodego_runtime',current_database(),'CREATE,TEMPORARY') "
            +"AND NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname::text !~ '^pg_' AND nspname<>'information_schema' "
            +"AND has_schema_privilege('bodego_runtime',oid,'CREATE')) AS no_ddl,"
            +"NOT has_table_privilege('bodego_runtime','public.flyway_schema_history','SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER') "
            +"AND NOT has_any_column_privilege('bodego_runtime','public.flyway_schema_history','SELECT,INSERT,UPDATE,REFERENCES') AS history_denied,"
            +"NOT EXISTS (SELECT 1 FROM (VALUES "+values(tables())+") t(name) WHERE NOT has_table_privilege('bodego_runtime',name,'SELECT') "
            +"OR NOT has_table_privilege('bodego_runtime',name,'INSERT') OR has_table_privilege('bodego_runtime',name,'DELETE,TRUNCATE,REFERENCES,TRIGGER')) AS table_permissions,"
            +"NOT EXISTS (SELECT 1 FROM (VALUES "+values(WRITABLE)+") t(name) WHERE NOT has_table_privilege('bodego_runtime',name,'UPDATE')) AS update_permissions,"
            +"NOT EXISTS (SELECT 1 FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid JOIN pg_namespace n ON n.oid=c.relnamespace "
            +"WHERE n.nspname='public' AND c.relname IN ('audit_logs','inventory_movements','physical_counts','stock_adjustments','wastes') "
            +"AND a.attnum>0 AND NOT a.attisdropped AND NOT (c.relname='physical_counts' AND a.attname='updated_at') "
            +"AND has_column_privilege('bodego_runtime',c.oid,a.attnum,'UPDATE')) "
            +"AND has_column_privilege('bodego_runtime','public.physical_counts','updated_at','UPDATE') AS append_permissions,"
            +"NOT EXISTS (SELECT 1 FROM (VALUES "+values(tables().stream().map(name->name+"_id_seq").toList())+") s(name) "
            +"WHERE NOT has_sequence_privilege('bodego_runtime',name,'USAGE') OR has_sequence_privilege('bodego_runtime',name,'SELECT,UPDATE')) AS identity_permissions";
    static final List<String> POST_FLAGS=List.of("role_flags","no_memberships","no_ownership","can_connect","no_ddl","history_denied",
            "table_permissions","update_permissions","append_permissions","identity_permissions");
    static boolean flag(PrintStream out,String key,boolean value) {out.println(key.toUpperCase(Locale.ROOT)+"="+value);return value;}
    static Statement statement(Connection connection) throws SQLException {var statement=connection.createStatement();statement.setQueryTimeout(10);return statement;}
    static boolean preflight(Connection connection,boolean creating,PrintStream out) throws SQLException {
        try(var statement=statement(connection);var rows=statement.executeQuery(PREFLIGHT_SQL)) {
            if(!rows.next()) throw new SQLException();boolean ok=true;
            boolean exists=rows.getBoolean("role_exists");out.println("RUNTIME_ROLE_EXISTS="+exists);
            ok=(creating?!exists:exists) && ok;
            for(String key:List.of("owns_database","owns_schema","owns_objects","dedicated_cluster","primary_server","distinct_owner"))
                ok=flag(out,key,rows.getBoolean(key)) && ok;
            if(creating) ok=flag(out,"can_create_role",rows.getBoolean("can_create_role")) && ok;
            return ok;
        }
    }
    static List<Long> snapshot(Connection connection) throws SQLException {
        try(var statement=statement(connection);var rows=statement.executeQuery(SNAPSHOT_SQL)) {
            if(!rows.next()) throw new SQLException();return List.of(rows.getLong(1),rows.getLong(2),rows.getLong(3));
        }
    }
    static void history(Connection connection) throws SQLException {
        try(var statement=statement(connection);var rows=statement.executeQuery(HISTORY_SQL)) {
            if(!rows.next() || !rows.getBoolean("history_ok")) throw new IllegalStateException("RUNTIME_HISTORY_REJECTED");
        }
    }
    static boolean verifyRole(Connection connection,PrintStream out) throws SQLException {
        try(var statement=statement(connection);var rows=statement.executeQuery(VERIFY_SQL)) {
            if(!rows.next()) throw new SQLException();boolean ok=true;
            for(String key:POST_FLAGS) ok=flag(out,key,rows.getBoolean(key)) && ok;
            return ok;
        }
    }
    static Properties properties(String user,char[] password,boolean readOnly) {
        var properties=StagingJdbcSupport.tls();properties.setProperty("user",user);properties.setProperty("password",new String(password));
        properties.setProperty("options","-c default_transaction_read_only="+(readOnly?"on":"off")+" -c statement_timeout=10000 -c lock_timeout=5000");
        return properties;
    }
    static void identity(Connection connection,StagingJdbcSupport.Target target,String user,boolean readOnly) throws SQLException {
        var identity=StagingJdbcSupport.identity(connection);
        if(!identity.matches(target.database(),target.port(),user,readOnly) || (!readOnly && identity.readOnly()))
            throw new SQLException("RUNTIME_IDENTITY_REJECTED");
    }
    static int check(String url,String owner,char[] ownerPassword,boolean expectAbsent,Connector connector,PrintStream out) {
        var properties=new Properties();
        try {
            var target=target(url);if(owner==null || owner.isBlank() || ROLE.equals(owner) || ownerPassword==null || ownerPassword.length==0) throw new IllegalArgumentException();
            properties=properties(owner,ownerPassword,true);
            try(var connection=connector.open(target.url(),properties)) {
                connection.setReadOnly(true);connection.setAutoCommit(false);
                try {
                    identity(connection,target,owner,true);
                    boolean ok=preflight(connection,expectAbsent,out);
                    if(!ok) return 2;
                    history(connection);boolean data=snapshot(connection).equals(List.of(1L,1L,1L));
                    ok=flag(out,"business_admin_state_matches",data) && ok;
                    if(!expectAbsent) ok=verifyRole(connection,out) && ok;
                    out.println("CHECK_ONLY=true");return ok?0:2;
                } finally {connection.rollback();}
            }
        } catch(Throwable failure) {out.println("RUNTIME_CHECK_FAILED=true");out.println("SQLSTATE="+StagingJdbcSupport.sqlState(failure));return 1;}
        finally {properties.clear();}
    }
    static int create(String url,String owner,char[] ownerPassword,char[] runtimePassword,String approval,Connector connector,PrintStream out) {
        String stage="TARGET",outcome="NOT_STARTED";Connection connection=null;boolean commitAttempted=false,committed=false;
        var properties=new Properties();
        try {
            var target=target(url);if(!approval(target).equals(approval)) throw new IllegalArgumentException();
            if(owner==null || owner.isBlank() || ROLE.equals(owner) || ownerPassword==null || ownerPassword.length==0) throw new IllegalArgumentException();
            passwordPolicy(runtimePassword);properties=properties(owner,ownerPassword,false);
            stage="CONNECTION";connection=connector.open(target.url(),properties);connection.setReadOnly(false);connection.setAutoCommit(false);
            stage="PREFLIGHT";identity(connection,target,owner,false);
            try(var statement=statement(connection);var ignored=statement.executeQuery(LOCK_SQL)) { /* Serialize only this controlled operation. */ }
            if(!preflight(connection,true,out)) throw new IllegalStateException();
            history(connection);var before=snapshot(connection);
            if(!before.equals(List.of(1L,1L,1L))) throw new IllegalStateException();
            stage="CREATE_ROLE";
            try(var statement=statement(connection)) {statement.execute("CREATE ROLE bodego_runtime NOLOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS");}
            stage="PASSWORD_SCRAM";
            char[] temporary=runtimePassword.clone();
            try {connection.unwrap(PGConnection.class).alterUserPassword(ROLE,temporary,"scram-sha-256");}
            finally {Arrays.fill(temporary,'\0');}
            stage="GRANTS";
            try(var statement=statement(connection)) {
                for(String sql:grantStatements()) statement.execute(sql);
                statement.execute("ALTER ROLE bodego_runtime LOGIN");
            }
            stage="VERIFY_BEFORE_COMMIT";
            if(!verifyRole(connection,out) || !before.equals(snapshot(connection))) throw new IllegalStateException();
            stage="COMMIT";commitAttempted=true;connection.commit();committed=true;outcome="COMMITTED";
            stage="CLOSE";connection.close();connection=null;
            stage="RUNTIME_LOGIN_CHECK";
            var runtimeProperties=properties(ROLE,runtimePassword,true);
            try(var runtime=connector.open(target.url(),runtimeProperties)) {
                runtime.setReadOnly(true);runtime.setAutoCommit(false);
                try {identity(runtime,target,ROLE,true);if(!before.equals(snapshot(runtime))) throw new IllegalStateException();}
                finally {runtime.rollback();}
            } finally {runtimeProperties.clear();}
            out.println("RUNTIME_ROLE_COMPLETE=true");out.println("RUNTIME_LOGIN_READ_ONLY=true");out.println("TRANSACTION_OUTCOME=COMMITTED");return 0;
        } catch(Throwable failure) {
            if(committed) outcome="COMMITTED";
            else if(commitAttempted) outcome="UNKNOWN";
            else if(connection!=null) {
                try {connection.rollback();outcome="ROLLED_BACK";} catch(SQLException ignored) {outcome="UNKNOWN";}
            }
            out.println("RUNTIME_FAILED_STAGE="+stage);out.println("SQLSTATE="+StagingJdbcSupport.sqlState(failure));
            out.println("TRANSACTION_OUTCOME="+outcome);out.println("RUNTIME_RETRY_AUTHORIZED=false");return 1;
        } finally {
            properties.clear();if(connection!=null) try {connection.close();} catch(SQLException ignored) {out.println("RUNTIME_CLEANUP_FAILED=true");}
        }
    }
    public static void main(String[] args) {
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
        java.util.logging.Logger.getLogger("org.postgresql").setLevel(java.util.logging.Level.OFF);
        var console=System.console();if(args.length!=0 || console==null) {System.err.println("STAGING_INTERACTIVE_TERMINAL_REQUIRED");System.exit(1);return;}
        char[] ownerPassword=null,runtimePassword=null,repeat=null;int exit=1;
        try {
            String mode=console.readLine("Mode PLAN (default, read-only), CHECK (existing role, read-only), or CREATE (separate authorization): ");
            if(mode==null || mode.isBlank()) mode="PLAN";
            if(!Set.of("PLAN","CHECK","CREATE").contains(mode)) throw new IllegalArgumentException();
            var target=target(console.readLine("Authorized external JDBC URL (:5432/bodego_staging, no credentials/options): "));
            String owner=console.readLine("Migration/owner role, never bodego_runtime: ");ownerPassword=console.readPassword("Owner password (hidden): ");
            String readApproval="AUTHORIZE READ ONLY ROLE CHECK "+target.url()+" public";
            console.printf("Type exactly for read-only preflight:%n%s%n",readApproval);
            if(!readApproval.equals(console.readLine("Approval: "))) throw new IllegalArgumentException();
            exit=check(target.url(),owner,ownerPassword,!mode.equals("CHECK"),DriverManager::getConnection,System.out);
            if(exit==0 && mode.equals("CREATE")) {
                runtimePassword=console.readPassword("New runtime password from private manager (24-128 non-space ASCII, hidden): ");
                repeat=console.readPassword("Repeat runtime password (hidden): ");
                passwordPolicy(runtimePassword);if(repeat==null || !Arrays.equals(runtimePassword,repeat)) throw new IllegalArgumentException();
                console.printf("No write is authorized yet. Separate approval must include cluster-global role and target PUBLIC ACL restrictions.%nType exactly:%n%s%n",approval(target));
                String answer=console.readLine("Write approval: ");
                exit=create(target.url(),owner,ownerPassword,runtimePassword,answer,DriverManager::getConnection,System.out);
            }
        } catch(Throwable failure) {System.err.println("RUNTIME_INPUT_FAILED SQLSTATE="+StagingJdbcSupport.sqlState(failure));exit=1;}
        finally {for(char[] secret:new char[][]{ownerPassword,runtimePassword,repeat}) if(secret!=null) Arrays.fill(secret,'\0');}
        if(exit!=0) System.exit(exit);
    }
}
