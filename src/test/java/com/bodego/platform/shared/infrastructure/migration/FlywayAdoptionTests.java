package com.bodego.platform.shared.infrastructure.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** No Spring context, environment credentials, local fallback, clean or schema reset. */
@Testcontainers
class FlywayAdoptionTests {
    private static final List<String> LEGACY = List.of(
            "alerts", "audit_logs", "categories", "correction_requests", "inventory_movements",
            "lots", "offers", "physical_counts", "products", "recommendations", "stock_adjustments", "wastes");
    private static final String MIGRATIONS = "classpath:db/migration";
    private static final String ADOPTION = "classpath:db/adoption/bodego-local";
    private static final String APPROVAL = "JhorchStore-EthanStore-approved-v1";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("bodego_migration_control");

    private String url;

    @BeforeEach
    void isolatedDatabase() throws SQLException {
        // Only a URL obtained from our running container is ever used.
        String database = "migration_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            connection.createStatement().execute("CREATE DATABASE " + database);
        }
        url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database;
    }

    private FluentConfiguration configuration(boolean adoption) {
        var configuration = Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema("public").schemas("public").createSchemas(false)
                .baselineVersion("1").baselineOnMigrate(false).cleanDisabled(true)
                .validateOnMigrate(true).outOfOrder(false)
                // These original adoption tests verify their explicit historical stages.
                // V4 has separate preservation/transaction tests below and is used by Spring tests.
                .target(adoption ? "3" : "2");
        if (adoption) {
            configuration.locations(MIGRATIONS, ADOPTION).placeholders(Map.of("bodegoAdoption", APPROVAL));
        } else {
            configuration.locations(MIGRATIONS);
        }
        return configuration;
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void execute(String sql) throws SQLException {
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String scalar(String sql) throws SQLException {
        try (var connection = connection(); var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private void legacyDatabase() throws SQLException {
        try (var connection = connection()) {
            // Simulates a pre-Flyway database, without creating any Flyway history.
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V1__capture_existing_business_schema.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/fixtures/legacy_data.sql"));
        }
        for (String table : LEGACY) {
            execute("ALTER TABLE public." + table + " ALTER COLUMN id RESTART WITH 50");
        }
    }

    private Map<String, String> legacySnapshot() throws SQLException {
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (String table : LEGACY) {
            snapshot.put(table + ".rows", scalar("SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY id)::text, '[]') FROM public." + table + " t"));
            snapshot.put(table + ".columns", scalar("SELECT jsonb_agg(jsonb_build_array(column_name, data_type, character_maximum_length, numeric_precision, numeric_scale, datetime_precision, is_nullable, column_default, identity_generation) ORDER BY ordinal_position)::text FROM information_schema.columns WHERE table_schema='public' AND table_name='" + table + "'"));
            snapshot.put(table + ".constraints", scalar("SELECT jsonb_agg(jsonb_build_array(conname, contype, pg_get_constraintdef(oid)) ORDER BY conname)::text FROM pg_constraint WHERE conrelid='public." + table + "'::regclass"));
            snapshot.put(table + ".indexes", scalar("SELECT jsonb_agg(jsonb_build_array(indexname, indexdef) ORDER BY indexname)::text FROM pg_indexes WHERE schemaname='public' AND tablename='" + table + "'"));
            // Sequences have readable columns but no PostgreSQL composite row type.
            snapshot.put(table + ".sequence", scalar("SELECT jsonb_build_object('last_value', last_value, 'log_cnt', log_cnt, 'is_called', is_called)::text FROM public." + table + "_id_seq"));
        }
        return snapshot;
    }

    @Test
    void v1AloneReproducesTwelveLegacyTablesBeforeIamIsAdded() throws SQLException {
        assertEquals(1, configuration(false).target("1").load().migrate().migrationsExecuted);
        assertEquals("12", scalar("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history'"));
        for (String table : LEGACY) {
            assertEquals(table, scalar("SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name='" + table + "'"));
        }
        assertNull(scalar("SELECT to_regclass('public.businesses')::text"));
        assertNull(scalar("SELECT to_regclass('public.users')::text"));
        assertEquals("2", scalar("SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND indexname IN ('ux_categories_business_normalized_name','ux_products_business_normalized_name')"));
        assertEquals(1, configuration(false).load().migrate().migrationsExecuted);
        assertEquals("businesses", scalar("SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name='businesses'"));
        assertEquals("users", scalar("SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name='users'"));
    }

    @Test
    void emptyDatabaseMigratesOnlyV1AndV2WithoutTenantData() throws SQLException {
        assertTrue(scalar("SHOW server_version").startsWith("18.6"));
        assertEquals(2, configuration(false).load().migrate().migrationsExecuted);
        assertEquals("12", scalar("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND column_name='business_id' AND table_name NOT IN ('users')"));
        assertEquals("0", scalar("SELECT count(*) FROM public.businesses"));
        assertEquals("0", scalar("SELECT count(*) FROM public.users"));
        assertEquals("12", scalar("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name NOT IN ('users','businesses','flyway_schema_history') AND is_identity='YES' AND identity_generation='BY DEFAULT'"));
    }

    @Test
    void explicitBaselinePreservesEveryLegacyTableAndAdoptsApprovedBusinesses() throws SQLException {
        legacyDatabase();
        var before = legacySnapshot();
        var flyway = configuration(true).load();
        flyway.baseline();
        assertEquals(2, flyway.migrate().migrationsExecuted);
        assertEquals(before, legacySnapshot());
        assertEquals("BASELINE", scalar("SELECT type FROM public.flyway_schema_history WHERE version='1'"));
        assertApprovedBusinesses();
        assertEquals("0", scalar("SELECT count(*) FROM public.users"));
        assertEquals("3", scalar("SELECT nextval(pg_get_serial_sequence('public.businesses','id'))"));
    }

    @Test
    void nonemptyDatabaseRequiresExplicitBaseline() throws SQLException {
        legacyDatabase();
        var before = legacySnapshot();
        assertThrows(FlywayException.class, () -> configuration(false).load().migrate());
        assertEquals(before, legacySnapshot());
        assertNull(scalar("SELECT to_regclass('public.businesses')::text"));
    }

    @Test
    void approvedAdoptionIsRepeatableWithoutDuplicateRowsOrSequenceReset() throws SQLException {
        var flyway = configuration(true).load();
        assertEquals(3, flyway.migrate().migrationsExecuted);
        assertApprovedBusinesses();
        execute("ALTER TABLE public.businesses ALTER COLUMN id RESTART WITH 100");
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals("100", scalar("SELECT nextval(pg_get_serial_sequence('public.businesses','id'))"));
        assertEquals("2", scalar("SELECT count(*) FROM public.businesses"));
    }

    @Test
    void adoptionNeverRewindsAnAdvancedIdentity() throws SQLException {
        configuration(false).load().migrate();
        execute("ALTER TABLE public.businesses ALTER COLUMN id RESTART WITH 100");
        configuration(true).load().migrate();
        assertEquals("100", scalar("SELECT nextval(pg_get_serial_sequence('public.businesses','id'))"));
    }

    @Test
    void failureAfterInsertsRollsBackBusinessesAndCanBeRetried() throws SQLException {
        configuration(false).load().migrate();
        // Causes the V3 identity guard to fail after its inserts, within Flyway's transaction.
        execute("ALTER SEQUENCE public.businesses_id_seq INCREMENT BY 2");
        assertThrows(FlywayException.class, () -> configuration(true).load().migrate());
        assertEquals("0", scalar("SELECT count(*) FROM public.businesses"));
        assertEquals("0", scalar("SELECT count(*) FROM public.flyway_schema_history WHERE version='3'"));
        execute("ALTER SEQUENCE public.businesses_id_seq INCREMENT BY 1");
        assertEquals(1, configuration(true).load().migrate().migrationsExecuted);
        assertApprovedBusinesses();
    }

    @Test
    void adoptionRejectsUnapprovedDatasetAndUnexpectedBusinessReferences() throws SQLException {
        configuration(false).load().migrate();
        assertThrows(FlywayException.class, () -> configuration(true)
                .placeholders(Map.of("bodegoAdoption", "not-approved")).load().migrate());
        execute("INSERT INTO public.categories(created_at,updated_at,business_id,name) VALUES(now(),now(),99,'Other tenant')");
        assertThrows(FlywayException.class, () -> configuration(true).load().migrate());
        assertEquals("0", scalar("SELECT count(*) FROM public.businesses"));
    }

    @Test
    void explicitAdoptionLocationStillRequiresDatasetConfirmation() throws SQLException {
        configuration(false).load().migrate();
        assertThrows(FlywayException.class, () -> configuration(false)
                .locations(MIGRATIONS, ADOPTION).target("3").load().migrate());
        assertEquals("0", scalar("SELECT count(*) FROM public.businesses"));
        assertEquals("0", scalar("SELECT count(*) FROM public.flyway_schema_history WHERE version='3'"));
    }

    @Test
    void existingHistoricalIdIsNotSilentlyOverwritten() throws SQLException {
        configuration(false).load().migrate();
        execute("INSERT INTO public.businesses VALUES(1,'Existing tenant','Existing address','123',true,now(),now())");
        assertThrows(FlywayException.class, () -> configuration(true).load().migrate());
        assertEquals("Existing tenant", scalar("SELECT name FROM public.businesses WHERE id=1"));
        assertEquals("1", scalar("SELECT count(*) FROM public.businesses"));
    }

    @Test
    void iamConstraintsEnforceNormalizedGlobalEmailRoleAndBusinessReference() throws SQLException {
        configuration(true).load().migrate();
        // Synthetic noncredential hash-shaped value: no real or usable user credentials.
        String insert = "INSERT INTO public.users(business_id,full_name,email,password_hash,user_role,active,created_at,updated_at) VALUES(%d,'Fixture user','%s','fixture-not-a-credential','%s',false,now(),now())";
        execute(insert.formatted(1, "fixture@example.invalid", "EMPLOYEE"));
        assertThrows(SQLException.class, () -> execute(insert.formatted(2, "fixture@example.invalid", "EMPLOYEE")));
        assertThrows(SQLException.class, () -> execute(insert.formatted(1, " Upper@example.invalid ", "EMPLOYEE")));
        assertThrows(SQLException.class, () -> execute(insert.formatted(99, "other@example.invalid", "EMPLOYEE")));
        assertThrows(SQLException.class, () -> execute(insert.formatted(1, "other@example.invalid", "OWNER")));
    }

    private void assertApprovedBusinesses() throws SQLException {
        assertEquals("JhorchStore|Av. Los Álamos 245, Santiago de Surco, Lima, Perú|985559483|true",
                scalar("SELECT name||'|'||address||'|'||phone||'|'||active FROM public.businesses WHERE id=1"));
        assertEquals("EthanStore|Jr. Las Palmeras 518, San Miguel, Lima, Perú|917927486|true",
                scalar("SELECT name||'|'||address||'|'||phone||'|'||active FROM public.businesses WHERE id=2"));
    }

    @Test
    void v4IsAvailableToNewInstallationsWithoutLocalAdoptionData() throws SQLException {
        var current=configuration(false).target("4").load();
        assertEquals(3,current.migrate().migrationsExecuted);
        current.validate();
        assertEquals("1,2,4",scalar("SELECT string_agg(version,',' ORDER BY installed_rank) FROM public.flyway_schema_history"));
        assertEquals("0",scalar("SELECT count(*) FROM public.businesses"));
        assertReasonWidths("500");
        assertEquals(0,current.migrate().migrationsExecuted);
    }

    @Test
    void v4PreservesExistingRowsConstraintsIndexesSequencesAndAllOtherColumns() throws SQLException {
        legacyDatabase();
        var adopted=configuration(true).load(); adopted.baseline(); adopted.migrate();
        // Represents a valid pending 500-character request created before this correction.
        execute("INSERT INTO public.physical_counts(created_at,updated_at,business_id,counted_at,counted_quantity,difference,lot_id,system_quantity,observation) VALUES(now(),now(),1,now(),2,-1,1,3,'A04 pending fixture')");
        execute("INSERT INTO public.correction_requests(created_at,updated_at,business_id,physical_count_id,reason,requested_at,status) VALUES(now(),now(),1,(SELECT id FROM public.physical_counts WHERE observation='A04 pending fixture'),repeat('r',500),now(),'PENDING')");
        var before=legacySnapshot();
        assertReasonWidths("255");
        var current=configuration(true).target("4").load();
        assertEquals(1,current.migrate().migrationsExecuted);
        current.validate(); assertReasonWidths("500");
        var after=legacySnapshot();
        for(String table:LEGACY) {
            String key=table+".columns";
            List<List<Object>> expected=com.jayway.jsonpath.JsonPath.read(before.remove(key),"$");
            if(List.of("inventory_movements","stock_adjustments").contains(table)) {
                var reason=expected.stream().filter(column->column.getFirst().equals("reason")).findFirst().orElseThrow();
                assertEquals(255,reason.get(2)); reason.set(2,500);
            }
            assertEquals(expected,com.jayway.jsonpath.JsonPath.read(after.remove(key),"$"),key);
        }
        assertEquals(before,after);
        assertEquals("500",scalar("SELECT length(reason) FROM public.correction_requests WHERE status='PENDING'"));
        assertEquals("2",scalar("SELECT count(*) FROM public.businesses"));
        assertEquals("0",scalar("SELECT count(*) FROM public.users"));
        assertEquals(0,current.migrate().migrationsExecuted);
    }

    @Test
    void failureOnSecondV4AlterRollsBackBothWidthsAndCanBeRetriedInIsolation() throws SQLException {
        configuration(false).load().migrate();
        execute("CREATE VIEW public.a04_block_widening AS SELECT reason FROM public.stock_adjustments");
        var before=legacySnapshot();
        var current=configuration(false).target("4").load();
        assertThrows(FlywayException.class,current::migrate);
        assertReasonWidths("255"); assertEquals(before,legacySnapshot());
        assertEquals("0",scalar("SELECT count(*) FROM public.flyway_schema_history WHERE version='4'"));
        execute("DROP VIEW public.a04_block_widening");
        assertEquals(1,current.migrate().migrationsExecuted);
        current.validate(); assertReasonWidths("500");
    }

    private void assertReasonWidths(String width) throws SQLException {
        for(String table:List.of("inventory_movements","stock_adjustments")) {
            assertEquals(width,scalar("SELECT character_maximum_length::text FROM information_schema.columns WHERE table_schema='public' AND table_name='"+table+"' AND column_name='reason'"));
        }
        assertEquals("500",scalar("SELECT character_maximum_length::text FROM information_schema.columns WHERE table_schema='public' AND table_name='correction_requests' AND column_name='reason'"));
    }
}
