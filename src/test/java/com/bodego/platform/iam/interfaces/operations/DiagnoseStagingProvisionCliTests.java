package com.bodego.platform.iam.interfaces.operations;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** No database, containers, migrations, Spring context or provisioning is started by these tests. */
class DiagnoseStagingProvisionCliTests {
    static final String URL="jdbc:postgresql://isolated-fixture.render.com:5432/bodego_staging";
    static final String ROLE="fixture_migrator";
    static ResultSet single() throws SQLException {
        var rows=mock(ResultSet.class);when(rows.next()).thenReturn(true,false);return rows;
    }
    static final class Fixture {
        final Connection connection=mock(Connection.class);
        final Statement statement=mock(Statement.class);
        final ResultSet identity=single(),objects=single(),permissions=single(),sequences=single(),businesses=single(),users=single();
        final Map<String,Boolean> flags=new HashMap<>();
        final List<String> queries=new ArrayList<>();
        final List<Object[]> history=new ArrayList<>(List.of(new Object[]{"1","SQL",true},new Object[]{"2","SQL",true},new Object[]{"4","SQL",true}));
        // Independent V2 schema fixture, not generated from the production expectation map.
        final List<String> columns=new ArrayList<>(List.of(
                "businesses.id:int8:0:d","businesses.name:varchar:100:","businesses.address:varchar:255:","businesses.phone:varchar:32:",
                "businesses.active:bool:0:","businesses.created_at:timestamptz:0:","businesses.updated_at:timestamptz:0:",
                "users.id:int8:0:d","users.business_id:int8:0:","users.full_name:varchar:150:","users.email:varchar:254:",
                "users.password_hash:varchar:512:","users.user_role:varchar:16:","users.active:bool:0:",
                "users.created_at:timestamptz:0:","users.updated_at:timestamptz:0:"));
        final ByteArrayOutputStream output=new ByteArrayOutputStream();
        final PrintStream out=new PrintStream(output,true,StandardCharsets.UTF_8);
        boolean tlsPropertiesChecked;
        Fixture() throws SQLException {
            when(connection.createStatement()).thenReturn(statement);
            when(identity.getString(1)).thenReturn("bodego_staging");when(identity.getString(2)).thenReturn("public");
            when(identity.getInt(3)).thenReturn(5432);when(identity.getInt(4)).thenReturn(180007);
            when(identity.getString(5)).thenReturn(ROLE);when(identity.getString(6)).thenReturn("on");when(identity.getBoolean(7)).thenReturn(true);
            for(var rows:List.of(objects,permissions,sequences)) when(rows.getBoolean(anyString())).thenAnswer(call->{
                String key=call.getArgument(0);return flags.getOrDefault(key,!key.equals("role_is_runtime"));
            });
            when(statement.executeQuery(anyString())).thenAnswer(call->{
                String sql=call.getArgument(0);queries.add(sql);
                return switch(sql) {
                    case StagingJdbcSupport.IDENTITY_SQL -> identity;
                    case DiagnoseStagingProvisionCli.OBJECTS_SQL -> objects;
                    case DiagnoseStagingProvisionCli.PERMISSIONS_SQL -> permissions;
                    case DiagnoseStagingProvisionCli.SEQUENCES_SQL -> sequences;
                    case DiagnoseStagingProvisionCli.BUSINESS_COUNT_SQL -> businesses;
                    case DiagnoseStagingProvisionCli.USER_COUNT_SQL -> users;
                    case DiagnoseStagingProvisionCli.HISTORY_SQL -> historyRows();
                    case DiagnoseStagingProvisionCli.COLUMNS_SQL -> columnRows();
                    default -> throw new AssertionError("Unexpected diagnostic query");
                };
            });
        }
        ResultSet historyRows() throws SQLException {
            var rows=mock(ResultSet.class);var index=new AtomicInteger(-1);
            when(rows.next()).thenAnswer(call->index.incrementAndGet()<history.size());
            when(rows.getString(anyInt())).thenAnswer(call->(String)history.get(index.get())[(int)call.getArgument(0)-1]);
            when(rows.getBoolean(3)).thenAnswer(call->history.get(index.get())[2]);return rows;
        }
        ResultSet columnRows() throws SQLException {
            var rows=mock(ResultSet.class);var index=new AtomicInteger(-1);
            when(rows.next()).thenAnswer(call->index.incrementAndGet()<columns.size());
            when(rows.getString(anyInt())).thenAnswer(call->{
                var parts=columns.get(index.get()).split(":",-1);String[] key=parts[0].split("\\.");
                return switch((int)call.getArgument(0)) {case 1->key[0];case 2->key[1];case 3->parts[1];case 5->parts[3];default->throw new AssertionError();};
            });
            when(rows.getBoolean(4)).thenReturn(true);
            when(rows.getInt(6)).thenAnswer(call->{int length=Integer.parseInt(columns.get(index.get()).split(":",-1)[2]);return length==0?-1:length+4;});
            return rows;
        }
        int run() {return run(URL);}
        int run(String url) {
            return DiagnoseStagingProvisionCli.diagnose(url,ROLE,"Fixture-db-password".toCharArray(),(jdbc,properties)->{
                assertEquals(URL,jdbc);
                assertEquals("verify-full",properties.getProperty("sslmode"));
                assertEquals("org.postgresql.ssl.DefaultJavaSSLFactory",properties.getProperty("sslfactory"));
                assertTrue(properties.getProperty("options").contains("default_transaction_read_only=on"));
                assertTrue(properties.getProperty("options").contains("statement_timeout=10000"));
                assertEquals(ROLE,properties.getProperty("user"));tlsPropertiesChecked=true;return connection;
            },out);
        }
        String report() {return output.toString(StandardCharsets.UTF_8);}
    }
    @Test void acceptsOnlyExplicitAuthorizedRenderDatabaseAndExactApproval() {
        var target=DiagnoseStagingProvisionCli.target(URL);
        assertDoesNotThrow(()->DiagnoseStagingProvisionCli.authorize(target,DiagnoseStagingProvisionCli.approval(target)));
        assertThrows(IllegalArgumentException.class,()->DiagnoseStagingProvisionCli.authorize(target,"yes"));
        for(String url:List.of("jdbc:postgresql://localhost:5432/bodego_staging","jdbc:postgresql://127.0.0.1:5432/bodego_staging",
                "jdbc:postgresql://bad.render.com.evil.invalid:5432/bodego_staging",URL.replace("bodego_staging","bodego_db"),
                URL.replace("bodego_staging","other_staging"),URL+"?sslmode=disable",URL.replace(":5432",":"),
                URL.replace("//","//fixture:secret@"),URL+"#fragment"))
            assertThrows(IllegalArgumentException.class,()->DiagnoseStagingProvisionCli.target(url));
    }
    @Test void rejectedTargetNeverConnectsOrExposesInput() throws Exception {
        var fixture=new Fixture();assertEquals(1,fixture.run(URL.replace("bodego_staging","bodego_db")));
        assertFalse(fixture.tlsPropertiesChecked);verifyNoInteractions(fixture.connection);
        assertTrue(fixture.report().contains("DIAGNOSIS_FAILED_STAGE=TARGET"));assertFalse(fixture.report().contains("Fixture-db-password"));
    }
    @Test void selectOnlyReadOnlyTransactionChecksAllPreconditionsAndAlwaysRollsBack() throws Exception {
        var fixture=new Fixture();assertEquals(0,fixture.run());assertTrue(fixture.tlsPropertiesChecked);
        var order=inOrder(fixture.connection,fixture.statement);
        order.verify(fixture.connection).setReadOnly(true);order.verify(fixture.connection).setAutoCommit(false);
        order.verify(fixture.connection).createStatement();order.verify(fixture.statement).setQueryTimeout(10);
        order.verify(fixture.statement).executeQuery(StagingJdbcSupport.IDENTITY_SQL);
        verify(fixture.connection).rollback();verify(fixture.connection).close();verify(fixture.connection,never()).commit();
        verify(fixture.statement,never()).execute(anyString());verify(fixture.statement,never()).executeUpdate(anyString());
        verify(fixture.statement,never()).executeBatch();
        assertEquals(8,fixture.queries.size());
        for(String sql:fixture.queries) {assertTrue(sql.startsWith("SELECT "));assertFalse(sql.contains(";"));assertFalse(sql.contains("set_config("));assertFalse(sql.contains("nextval("));}
        assertTrue(fixture.report().contains("DATABASE_PRECONDITIONS_PASS=true"));
        assertTrue(fixture.report().contains("BUSINESSES_COUNT=0"));assertTrue(fixture.report().contains("ADMIN_COUNT=0"));
        assertTrue(fixture.report().contains("PERSONAL_INPUTS_CHECKED=false"));assertTrue(fixture.report().contains("PROVISION_EXECUTED=false"));
        assertFalse(fixture.report().contains(ROLE));assertFalse(fixture.report().contains("Fixture-db-password"));
    }
    @Test void runtimeWithoutFlywaySelectIsIdentifiedWithoutAbortingOtherChecks() throws Exception {
        var fixture=new Fixture();fixture.flags.put("role_is_runtime",true);fixture.flags.put("history_select",false);
        assertEquals(2,fixture.run());assertTrue(fixture.report().contains("FAILED_PRECONDITION=HISTORY_SELECT"));
        assertTrue(fixture.report().contains("ROLE_IS_RUNTIME=true"));assertTrue(fixture.report().contains("HISTORY_CHECKED=false"));
        assertFalse(fixture.queries.contains(DiagnoseStagingProvisionCli.HISTORY_SQL));
        assertTrue(fixture.report().contains("BUSINESSES_COUNT=0"));assertTrue(fixture.report().contains("USERS_COUNT=0"));
    }
    @Test void baselineIsNotMistakenForSqlV1() throws Exception {
        var fixture=new Fixture();fixture.history.set(0,new Object[]{"1","BASELINE",true});
        assertEquals(2,fixture.run());assertTrue(fixture.report().contains("FAILED_PRECONDITION=HISTORY_SQL_124_MATCH"));
        assertTrue(fixture.report().contains("HISTORY_BASELINE_COUNT=1"));
    }
    @Test void existingBusinessAdminAreReportedWithoutPersonalData() throws Exception {
        var fixture=new Fixture();when(fixture.businesses.getLong(1)).thenReturn(1L);when(fixture.users.getLong(anyInt())).thenReturn(1L);
        assertEquals(2,fixture.run());assertTrue(fixture.report().contains("FAILED_PRECONDITION=BUSINESSES_EMPTY"));
        assertTrue(fixture.report().contains("FAILED_PRECONDITION=USERS_EMPTY"));assertTrue(fixture.report().contains("ACTIVE_ADMIN_COUNT=1"));
        assertTrue(fixture.queries.stream().noneMatch(sql->sql.contains("SELECT email") || sql.contains("SELECT password_hash") || sql.contains("SELECT full_name")));
    }
    @Test void narrowerPasswordHashColumnIsIncompatibleWithoutPrintingAnyHash() throws Exception {
        var fixture=new Fixture();fixture.columns.replaceAll(column->column.replace("users.password_hash:varchar:512:","users.password_hash:varchar:255:"));
        assertEquals(2,fixture.run());assertTrue(fixture.report().contains("FAILED_PRECONDITION=COLUMNS_COMPATIBLE"));
        assertTrue(fixture.report().contains("INCOMPATIBLE_COLUMN=users.password_hash"));
        assertFalse(fixture.queries.contains(DiagnoseStagingProvisionCli.SEQUENCES_SQL));
    }
    @Test void missingTableIsIdentifiedAndNeverSelected() throws Exception {
        var fixture=new Fixture();fixture.flags.put("users_exists",false);fixture.flags.put("users_select",false);
        fixture.columns.removeIf(column->column.startsWith("users."));
        assertEquals(2,fixture.run());assertTrue(fixture.report().contains("FAILED_PRECONDITION=USERS_EXISTS"));
        assertTrue(fixture.report().contains("MISSING_COLUMN=users.id"));
        assertFalse(fixture.queries.contains(DiagnoseStagingProvisionCli.USER_COUNT_SQL));
    }
    @Test void permissionFailuresReportOnlySanitizedSqlStateAndRollback() throws Exception {
        var fixture=new Fixture();when(fixture.statement.executeQuery(DiagnoseStagingProvisionCli.HISTORY_SQL))
                .thenThrow(new SQLException("Fixture secret password/hash/person@example.invalid","42501"));
        assertEquals(1,fixture.run());assertTrue(fixture.report().contains("SQLSTATE=42501"));
        assertTrue(fixture.report().contains("DIAGNOSIS_FAILED_STAGE=HISTORY"));
        assertFalse(fixture.report().contains("person@example.invalid"));assertFalse(fixture.report().contains("Fixture secret"));
        verify(fixture.connection).rollback();verify(fixture.connection).close();verify(fixture.connection,never()).commit();
    }
    @Test void unsafeSqlStatesAndExceptionMessagesAreNeverPrinted() {
        assertEquals("UNAVAILABLE",DiagnoseStagingProvisionCli.sqlState(new SQLException("secret","42501\nfixture@example.invalid")));
        assertEquals("UNAVAILABLE",DiagnoseStagingProvisionCli.sqlState(new IllegalStateException("fixture-secret")));
        assertEquals("08006",DiagnoseStagingProvisionCli.sqlState(new IllegalStateException("secret",new SQLException("secret","08006"))));
    }
    @Test void connectionFailureIsSanitizedBeforeAnyQuery() throws Exception {
        var fixture=new Fixture();
        int result=DiagnoseStagingProvisionCli.diagnose(URL,ROLE,"Fixture-db-password".toCharArray(),(url,properties)->{
            throw new SQLException("fixture-sensitive-authentication-details","28P01");
        },fixture.out);
        assertEquals(1,result);assertTrue(fixture.report().contains("SQLSTATE=28P01"));
        assertTrue(fixture.report().contains("DIAGNOSIS_FAILED_STAGE=CONNECT"));
        assertFalse(fixture.report().contains("fixture-sensitive"));verifyNoInteractions(fixture.connection);
    }
    @Test void cleanupFailureNeverReportsCompletedDiagnosisOrLeaksDriverDetails() throws Exception {
        var fixture=new Fixture();doThrow(new SQLException("fixture-sensitive-network-details","08006")).when(fixture.connection).rollback();
        assertEquals(1,fixture.run());assertFalse(fixture.report().contains("DIAGNOSIS_COMPLETE=true"));
        assertTrue(fixture.report().contains("DIAGNOSIS_FAILED_STAGE=CLEANUP"));assertTrue(fixture.report().contains("SQLSTATE=08006"));
        assertFalse(fixture.report().contains("fixture-sensitive"));verify(fixture.connection).close();
    }
    @Test void wrongIdentityTlsOrReadOnlyStopsBeforeMetadataQueries() throws Exception {
        for(String failure:List.of("database","role","schema","tls","readonly","version","port")) {
            var fixture=new Fixture();
            switch(failure) {
                case "database"->when(fixture.identity.getString(1)).thenReturn("bodego_db");
                case "role"->when(fixture.identity.getString(5)).thenReturn("other_role");
                case "schema"->when(fixture.identity.getString(2)).thenReturn("other_schema");
                case "tls"->when(fixture.identity.getBoolean(7)).thenReturn(false);
                case "readonly"->when(fixture.identity.getString(6)).thenReturn("off");
                case "version"->when(fixture.identity.getInt(4)).thenReturn(190000);
                case "port"->when(fixture.identity.getInt(3)).thenReturn(5433);
            }
            assertEquals(2,fixture.run());assertEquals(List.of(StagingJdbcSupport.IDENTITY_SQL),fixture.queries);
            assertTrue(fixture.report().contains("DIAGNOSIS_COMPLETE=false"));verify(fixture.connection).rollback();
        }
    }
    @Test void sharedTlsGuardPreservesDestinationAndIdentityRequirements() throws Exception {
        assertEquals("verify-full",StagingJdbcSupport.tls().getProperty("sslmode"));
        var remote=StagingJdbcSupport.parse(URL);assertEquals("bodego_staging",remote.database());
        var fixture=new Fixture();assertTrue(StagingJdbcSupport.identity(fixture.connection).matches(remote.database(),remote.port(),ROLE,true));
        when(fixture.identity.next()).thenReturn(true,false);when(fixture.identity.getBoolean(7)).thenReturn(false);
        assertFalse(StagingJdbcSupport.identity(fixture.connection).matches(remote.database(),remote.port(),ROLE,true));
    }
}
