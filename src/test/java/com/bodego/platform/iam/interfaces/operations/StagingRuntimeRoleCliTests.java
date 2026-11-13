package com.bodego.platform.iam.interfaces.operations;

import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** JDBC/PGConnection are mocks. No roles, DDL, SQL, network, migration or business service is executed. */
class StagingRuntimeRoleCliTests {
    static final String URL="jdbc:postgresql://fixture.render.com:5432/bodego_staging",OWNER="fixture_owner";
    static char[] ownerPassword() {return "Fixture-owner-password".toCharArray();}
    static char[] runtimePassword() {return "Fixture-runtime-password-from-manager".toCharArray();}
    static ResultSet row() throws SQLException {var row=mock(ResultSet.class);when(row.next()).thenReturn(true,false);return row;}
    static final class Fixture {
        final Connection owner=mock(Connection.class),runtime=mock(Connection.class);
        final Statement ownerStatement=mock(Statement.class),runtimeStatement=mock(Statement.class);
        final PGConnection pg=mock(PGConnection.class);
        final Map<String,Boolean> flags=new HashMap<>();final List<String> queries=new ArrayList<>(),ddl=new ArrayList<>();
        final AtomicBoolean ownerReadOnly=new AtomicBoolean(),runtimeReadOnly=new AtomicBoolean();
        final AtomicInteger snapshots=new AtomicInteger();
        final ByteArrayOutputStream output=new ByteArrayOutputStream();final PrintStream out=new PrintStream(output,true,StandardCharsets.UTF_8);
        boolean roleExists,changeCountsAfterGrant;int connects;char[] driverPassword;
        Fixture() throws Exception {
            when(owner.createStatement()).thenReturn(ownerStatement);when(runtime.createStatement()).thenReturn(runtimeStatement);
            when(owner.unwrap(PGConnection.class)).thenReturn(pg);
            doAnswer(call->{ownerReadOnly.set(call.getArgument(0));return null;}).when(owner).setReadOnly(anyBoolean());
            doAnswer(call->{runtimeReadOnly.set(call.getArgument(0));return null;}).when(runtime).setReadOnly(anyBoolean());
            when(ownerStatement.executeQuery(anyString())).thenAnswer(call->query(call.getArgument(0),false));
            when(runtimeStatement.executeQuery(anyString())).thenAnswer(call->query(call.getArgument(0),true));
            when(ownerStatement.execute(anyString())).thenAnswer(call->{ddl.add(call.getArgument(0));return false;});
            doAnswer(call->{
                assertEquals("bodego_runtime",call.getArgument(0));assertEquals("scram-sha-256",call.getArgument(2));
                driverPassword=call.getArgument(1);assertArrayEquals(runtimePassword(),driverPassword);return null;
            }).when(pg).alterUserPassword(anyString(),any(char[].class),anyString());
        }
        ResultSet query(String sql,boolean isRuntime) throws SQLException {
            queries.add(sql);var result=row();
            if(sql.equals(StagingJdbcSupport.IDENTITY_SQL)) {
                when(result.getString(1)).thenReturn("bodego_staging");when(result.getString(2)).thenReturn("public");
                when(result.getInt(3)).thenReturn(5432);when(result.getInt(4)).thenReturn(180007);
                when(result.getString(5)).thenReturn(isRuntime?"bodego_runtime":OWNER);
                when(result.getString(6)).thenAnswer(call->(isRuntime?runtimeReadOnly:ownerReadOnly).get()?"on":"off");
                when(result.getBoolean(7)).thenReturn(true);
            } else if(sql.equals(CreateStagingRuntimeRoleCli.PREFLIGHT_SQL)) {
                when(result.getBoolean(anyString())).thenAnswer(call->{String key=call.getArgument(0);return key.equals("role_exists")?roleExists:flags.getOrDefault(key,true);});
            } else if(sql.equals(CreateStagingRuntimeRoleCli.SNAPSHOT_SQL)) {
                int number=snapshots.incrementAndGet();
                when(result.getLong(anyInt())).thenReturn(changeCountsAfterGrant && number>1?2L:1L);
            } else if(sql.equals(CreateStagingRuntimeRoleCli.HISTORY_SQL)) {
                when(result.getBoolean("history_ok")).thenReturn(flags.getOrDefault("history_ok",true));
            } else if(sql.equals(CreateStagingRuntimeRoleCli.VERIFY_SQL)) {
                when(result.getBoolean(anyString())).thenAnswer(call->flags.getOrDefault(call.getArgument(0),true));
            } else if(!sql.equals(CreateStagingRuntimeRoleCli.LOCK_SQL)) throw new AssertionError("Unexpected query");
            return result;
        }
        Connection connect(String url,Properties properties) {
            connects++;assertEquals(URL,url);assertEquals("verify-full",properties.getProperty("sslmode"));
            assertEquals("org.postgresql.ssl.DefaultJavaSSLFactory",properties.getProperty("sslfactory"));
            return properties.getProperty("user").equals(OWNER)?owner:runtime;
        }
        int create() {return CreateStagingRuntimeRoleCli.create(URL,OWNER,ownerPassword(),runtimePassword(),
                CreateStagingRuntimeRoleCli.approval(CreateStagingRuntimeRoleCli.target(URL)),this::connect,out);}
        String report() {String report=output.toString(StandardCharsets.UTF_8);assertFalse(report.contains("Fixture-"));return report;}
    }
    @Test void onlyExactRemoteStagingAndApprovalCanConnect() throws Exception {
        for(String url:List.of(URL.replace("bodego_staging","bodego_db"),URL.replace("bodego_staging","other_database"),
                URL.replace("fixture.render.com","localhost"),URL.replace("fixture.render.com","evil.invalid"),URL+"?sslmode=disable"))
            assertThrows(IllegalArgumentException.class,()->CreateStagingRuntimeRoleCli.target(url));
        var fixture=new Fixture();assertEquals(1,CreateStagingRuntimeRoleCli.create(URL,OWNER,ownerPassword(),runtimePassword(),"yes",fixture::connect,fixture.out));
        assertEquals(0,fixture.connects);assertTrue(fixture.ddl.isEmpty());
    }
    @Test void weakWhitespaceAndControlPasswordsAreRejectedWithoutConnection() throws Exception {
        for(char[] password:List.of("short".toCharArray(),("x".repeat(23)+" ").toCharArray(),("x".repeat(23)+"\0").toCharArray(),"x".repeat(129).toCharArray()))
            assertThrows(IllegalArgumentException.class,()->CreateStagingRuntimeRoleCli.passwordPolicy(password));
        var fixture=new Fixture();assertEquals(1,CreateStagingRuntimeRoleCli.create(URL,"bodego_runtime",ownerPassword(),runtimePassword(),
                CreateStagingRuntimeRoleCli.approval(CreateStagingRuntimeRoleCli.target(URL)),fixture::connect,fixture.out));
        assertEquals(0,fixture.connects);
    }
    @Test void checkIsReadOnlyAndNeverCreatesChangesPasswordOrCommits() throws Exception {
        var fixture=new Fixture();fixture.roleExists=true;
        assertEquals(0,CreateStagingRuntimeRoleCli.check(URL,OWNER,ownerPassword(),false,fixture::connect,fixture.out));
        assertTrue(fixture.ownerReadOnly.get());assertTrue(fixture.queries.stream().allMatch(sql->sql.startsWith("SELECT ")));
        assertTrue(fixture.ddl.isEmpty());verifyNoInteractions(fixture.pg);verify(fixture.owner,never()).commit();verify(fixture.owner).rollback();
        assertTrue(fixture.report().contains("CHECK_ONLY=true"));
    }
    @Test void planChecksEligibilityWithAbsentRoleWithoutAnyDdlOrPasswordOperation() throws Exception {
        var fixture=new Fixture();assertEquals(0,CreateStagingRuntimeRoleCli.check(URL,OWNER,ownerPassword(),true,fixture::connect,fixture.out));
        assertTrue(fixture.report().contains("RUNTIME_ROLE_EXISTS=false"));assertTrue(fixture.report().contains("CAN_CREATE_ROLE=true"));
        assertTrue(fixture.ownerReadOnly.get());assertTrue(fixture.ddl.isEmpty());verifyNoInteractions(fixture.pg);
        verify(fixture.owner,never()).commit();verify(fixture.owner).rollback();
    }
    @Test void creationUsesFixedStatementsClientScramAndReadOnlyPostLogin() throws Exception {
        var fixture=new Fixture();assertEquals(0,fixture.create());
        assertEquals(2,fixture.connects);assertFalse(fixture.ownerReadOnly.get());assertTrue(fixture.runtimeReadOnly.get());
        assertEquals("CREATE ROLE bodego_runtime NOLOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS",fixture.ddl.getFirst());
        assertEquals("ALTER ROLE bodego_runtime LOGIN",fixture.ddl.getLast());
        assertTrue(fixture.ddl.stream().noneMatch(sql->sql.contains("PASSWORD") || sql.contains("Fixture-") || sql.contains("bodego_db")));
        assertTrue(fixture.ddl.contains("REVOKE CREATE, TEMPORARY ON DATABASE bodego_staging FROM PUBLIC, bodego_runtime"));
        assertNotNull(fixture.driverPassword);assertArrayEquals(new char[fixture.driverPassword.length],fixture.driverPassword);
        verify(fixture.owner,times(1)).commit();verify(fixture.owner,never()).rollback();verify(fixture.runtime).rollback();
        assertTrue(fixture.report().contains("RUNTIME_LOGIN_READ_ONLY=true"));
    }
    @Test void existingRoleIsNeverOverwrittenOrGivenAnotherPassword() throws Exception {
        var fixture=new Fixture();fixture.roleExists=true;assertEquals(1,fixture.create());
        assertTrue(fixture.ddl.isEmpty());verifyNoInteractions(fixture.pg);verify(fixture.owner,never()).commit();
        verify(fixture.owner).rollback();assertTrue(fixture.report().contains("RUNTIME_ROLE_EXISTS=true"));
    }
    @Test void insufficientRoleOwnerPrivilegesOrSharedClusterPreventAllDdl() throws Exception {
        for(String flag:List.of("can_create_role","owns_database","owns_schema","owns_objects","dedicated_cluster","primary_server","distinct_owner","history_ok")) {
            var fixture=new Fixture();fixture.flags.put(flag,false);assertEquals(1,fixture.create());
            assertTrue(fixture.ddl.isEmpty());verifyNoInteractions(fixture.pg);verify(fixture.owner,never()).commit();
        }
    }
    @Test void unexpectedlyBroadGrantsOrChangedBusinessCountsRollbackCreation() throws Exception {
        for(String flag:CreateStagingRuntimeRoleCli.POST_FLAGS) {
            var fixture=new Fixture();fixture.flags.put(flag,false);assertEquals(1,fixture.create());
            verify(fixture.owner).rollback();verify(fixture.owner,never()).commit();assertEquals(1,fixture.connects);
        }
        var changed=new Fixture();changed.changeCountsAfterGrant=true;assertEquals(1,changed.create());
        verify(changed.owner).rollback();verify(changed.owner,never()).commit();
    }
    @Test void passwordErrorsAreSanitizedAndRolledBackWithoutLoggingInputs() throws Exception {
        var fixture=new Fixture();doThrow(new SQLException("Fixture-sensitive-verifier-do-not-log","42501")).when(fixture.pg)
                .alterUserPassword(anyString(),any(char[].class),anyString());
        assertEquals(1,fixture.create());assertTrue(fixture.report().contains("RUNTIME_FAILED_STAGE=PASSWORD_SCRAM"));
        assertTrue(fixture.report().contains("SQLSTATE=42501"));assertTrue(fixture.report().contains("TRANSACTION_OUTCOME=ROLLED_BACK"));
        verify(fixture.owner).rollback();verify(fixture.owner,never()).commit();
    }
    @Test void ambiguousCommitNeverClaimsRollbackOrRetriesDdl() throws Exception {
        var fixture=new Fixture();doThrow(new SQLException("Fixture-sensitive-network","08006")).when(fixture.owner).commit();
        assertEquals(1,fixture.create());assertTrue(fixture.report().contains("TRANSACTION_OUTCOME=UNKNOWN"));
        assertTrue(fixture.report().contains("RUNTIME_RETRY_AUTHORIZED=false"));verify(fixture.owner,times(1)).commit();
        verify(fixture.owner,never()).rollback();assertEquals(1,fixture.connects);
    }
    @Test void failedPostCommitLoginLeavesCommittedRoleAndNoRetry() throws Exception {
        var fixture=new Fixture();
        int code=CreateStagingRuntimeRoleCli.create(URL,OWNER,ownerPassword(),runtimePassword(),
                CreateStagingRuntimeRoleCli.approval(CreateStagingRuntimeRoleCli.target(URL)),(url,properties)->{
                    if(properties.getProperty("user").equals("bodego_runtime")) throw new SQLException("Fixture-sensitive-authentication","28P01");
                    return fixture.connect(url,properties);
                },fixture.out);
        assertEquals(1,code);assertTrue(fixture.report().contains("TRANSACTION_OUTCOME=COMMITTED"));
        assertTrue(fixture.report().contains("RUNTIME_FAILED_STAGE=RUNTIME_LOGIN_CHECK"));
        verify(fixture.owner,never()).rollback();
    }
    @Test void applicationTableAndSequenceGrantsMatchExistingReviewedScript() throws Exception {
        String script=Files.readString(Path.of("scripts/staging/grant-runtime.sql"));
        for(var check:List.of(
                Map.entry("GRANT SELECT, INSERT, UPDATE ON",CreateStagingRuntimeRoleCli.WRITABLE),
                Map.entry("GRANT SELECT, INSERT ON",CreateStagingRuntimeRoleCli.APPEND),
                Map.entry("GRANT USAGE ON SEQUENCE",CreateStagingRuntimeRoleCli.tables().stream().map(name->name+"_id_seq").toList()))) {
            var matcher=java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(check.getKey())+" (.*?) TO bodego_runtime;",java.util.regex.Pattern.DOTALL).matcher(script);
            assertTrue(matcher.find());var names=new HashSet<>(Arrays.asList(matcher.group(1).replaceAll("\\s+","").split(",")));
            assertEquals(new HashSet<>(check.getValue().stream().map(name->"public."+name).toList()),names);
        }
        assertTrue(CreateStagingRuntimeRoleCli.grantStatements().contains("GRANT UPDATE (updated_at) ON public.physical_counts TO bodego_runtime"));
        assertTrue(CreateStagingRuntimeRoleCli.VERIFY_SQL.contains("AS history_denied"));
        assertTrue(CreateStagingRuntimeRoleCli.VERIFY_SQL.contains("AS no_memberships"));
    }
}
