package com.bodego.platform.iam.interfaces.operations;

import java.net.URI;
import java.sql.*;
import java.util.*;

/** Shared operational JDBC guard. No Spring runtime, provisioning or migration dependency. */
final class StagingJdbcSupport {
    private StagingJdbcSupport() {}
    record Target(String url,String host,int port,String database) {}
    static Target parse(String url) {
        try {
            if(url==null || !url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();
            var uri=URI.create(url.substring(5));String host=uri.getHost();
            String database=uri.getPath()==null?"":uri.getPath().substring(1);
            if(host==null || !host.endsWith(".render.com") || uri.getPort()!=5432
                    || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                    || !database.matches("[A-Za-z][A-Za-z0-9_]*")
                    || Set.of("bodego_db","postgres","template0","template1").contains(database.toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException();
            return new Target(url,host,5432,database);
        } catch(RuntimeException failure) {throw new IllegalArgumentException("STAGING_TARGET_REJECTED");}
    }
    static Properties tls() {
        var properties=new Properties();
        properties.setProperty("sslmode","verify-full");
        properties.setProperty("sslfactory","org.postgresql.ssl.DefaultJavaSSLFactory");
        properties.setProperty("connectTimeout","5");properties.setProperty("socketTimeout","30");
        return properties;
    }
    static String sqlState(Throwable error) {
        var visited=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        for(int i=0;error!=null && i<16 && visited.add(error);i++,error=error.getCause()) {
            if(error instanceof SQLException sql && sql.getSQLState()!=null && sql.getSQLState().matches("[0-9A-Z]{5}"))
                return sql.getSQLState();
        }
        return "UNAVAILABLE";
    }
    static final String IDENTITY_SQL="SELECT current_database(),current_schema(),inet_server_port(),"
            +"current_setting('server_version_num')::int,current_user,current_setting('transaction_read_only'),"
            +"(SELECT ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid())";
    record Identity(String database,String schema,int port,int version,String role,boolean readOnly,boolean tls) {
        boolean matches(String expectedDatabase,int expectedPort,String expectedRole,boolean requireReadOnly) {
            return expectedDatabase.equals(database) && "public".equals(schema) && expectedPort==port
                    && version>=180000 && version<190000 && expectedRole.equals(role) && tls && (!requireReadOnly || readOnly);
        }
    }
    static Identity identity(Connection connection) throws SQLException {
        try(var statement=connection.createStatement()) {
            statement.setQueryTimeout(10);
            try(var rows=statement.executeQuery(IDENTITY_SQL)) {
                if(!rows.next()) throw new SQLException("STAGING_IDENTITY_REJECTED");
                return new Identity(rows.getString(1),rows.getString(2),rows.getInt(3),rows.getInt(4),
                        rows.getString(5),"on".equals(rows.getString(6)),rows.getBoolean(7));
            }
        }
    }
}
