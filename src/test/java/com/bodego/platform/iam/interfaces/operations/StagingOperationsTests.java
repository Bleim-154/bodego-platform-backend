package com.bodego.platform.iam.interfaces.operations;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import javax.net.ssl.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class StagingOperationsTests {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("bodego_remote_staging_isolated")
            .withCommand("bash","-c","openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=localhost "
                    +"-addext subjectAltName=DNS:localhost -keyout /tmp/staging.key -out /tmp/staging.crt >/dev/null 2>&1 && "
                    +"chmod 600 /tmp/staging.key && chown postgres:postgres /tmp/staging.key /tmp/staging.crt && "
                    +"exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/tmp/staging.crt -c ssl_key_file=/tmp/staging.key");
    static SSLContext original;
    static JdbcTemplate jdbc;
    static ProvisionStagingBusinessAdminCli.Target target;
    static char[] password() {return POSTGRES.getPassword().toCharArray();}
    @BeforeAll static void trustOnlyOwnedFixture() throws Exception {
        original=SSLContext.getDefault();
        var certificate=POSTGRES.copyFileFromContainer("/tmp/staging.crt",stream->CertificateFactory.getInstance("X.509").generateCertificate(stream));
        var store=KeyStore.getInstance(KeyStore.getDefaultType());store.load(null);store.setCertificateEntry("isolated",certificate);
        var trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());trust.init(store);
        var tls=SSLContext.getInstance("TLS");tls.init(null,trust.getTrustManagers(),null);SSLContext.setDefault(tls);
        String url="jdbc:postgresql://localhost:"+POSTGRES.getMappedPort(5432)+"/"+POSTGRES.getDatabaseName();
        // Direct fixture constructor only. Production CLI parse rejects local destinations before connecting.
        target=new ProvisionStagingBusinessAdminCli.Target(url,"localhost",5432,POSTGRES.getDatabaseName());
        jdbc=new JdbcTemplate(new DriverManagerDataSource(url,POSTGRES.getUsername(),POSTGRES.getPassword()));
        assertNotEquals("bodego_db",jdbc.queryForObject("select current_database()",String.class));
    }
    @AfterAll static void restoreTrust() {if(original!=null) SSLContext.setDefault(original);}
    @BeforeEach void emptyOwnedSchema() {
        assertEquals(POSTGRES.getDatabaseName(),jdbc.queryForObject("select current_database()",String.class));
        jdbc.execute("DROP SCHEMA public CASCADE"); jdbc.execute("CREATE SCHEMA public");
    }
    void migrate() throws Exception {MigrateNewStagingCli.migrate(target,POSTGRES.getUsername(),password());}
    ProvisionStagingBusinessAdminCli.Result provision(String email) {
        try(var context=ProvisioningRuntime.open(target.url(),POSTGRES.getUsername(),password(),ProvisionStagingBusinessAdminCli.tls())) {
            return ProvisionStagingBusinessAdminCli.provision(context,target,POSTGRES.getUsername(),"Staging fixture","Address","12345",
                    "Fixture admin",email,"Fixture-password-2026".toCharArray());
        }
    }
    @Test void destinationsAndAuthorizationAreExplicitAndRejectLocalOriginalAndOptions() {
        for(String url:List.of("jdbc:postgresql://localhost:5432/staging","jdbc:postgresql://db.render.com:5432/bodego_db",
                "jdbc:postgresql://db.render.com:5432/BODEGO_DB","jdbc:postgresql://db.render.com/staging",
                "jdbc:postgresql://db.render.com:5432/staging?sslmode=disable","jdbc:postgresql://user:pass@db.render.com:5432/staging",
                "jdbc:postgresql://db.render.com.evil.invalid:5432/staging"))
            assertEquals("STAGING_TARGET_REJECTED",assertThrows(IllegalArgumentException.class,
                    ()->ProvisionStagingBusinessAdminCli.Target.parse(url)).getMessage());
        var valid=ProvisionStagingBusinessAdminCli.Target.parse("jdbc:postgresql://db.render.com:5432/staging");
        assertThrows(IllegalArgumentException.class,()->valid.authorize("yes"));valid.authorize(valid.approval());
    }
    @Test void tlsMigrationInstallsOnlyOneTwoFourAndRejectsNonEmptyRetry() throws Exception {
        ProvisionStagingBusinessAdminCli.inspect(target,POSTGRES.getUsername(),password()); migrate();
        assertEquals(List.of("1","2","4"),jdbc.queryForList("SELECT version FROM flyway_schema_history ORDER BY installed_rank",String.class));
        assertEquals(15,jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public'",Integer.class));
        assertThrows(java.sql.SQLException.class,this::migrate);
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM users",Integer.class));
    }
    @Test void tlsRejectsWrongHostnameAndIdentityBeforeWriting() throws Exception {
        var wrong=new ProvisionStagingBusinessAdminCli.Target(target.url().replace("localhost","127.0.0.1"),"127.0.0.1",5432,target.database());
        assertThrows(java.sql.SQLException.class,()->ProvisionStagingBusinessAdminCli.inspect(wrong,POSTGRES.getUsername(),password()));
        var identity=new ProvisionStagingBusinessAdminCli.Target(target.url(),target.host(),5432,"wrong_database");
        assertThrows(java.sql.SQLException.class,()->ProvisionStagingBusinessAdminCli.inspect(identity,POSTGRES.getUsername(),password()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public'",Integer.class));
    }
    @Test void createsBusinessAndBcryptAdminAtomicallyAndRejectsDuplicateBootstrap() throws Exception {
        migrate();var result=provision(" FIXTURE@EXAMPLE.INVALID ");
        assertEquals(1L,result.businessId());assertEquals(1L,result.userId());
        assertEquals("fixture@example.invalid",jdbc.queryForObject("SELECT email FROM users",String.class));
        assertEquals("ADMIN",jdbc.queryForObject("SELECT user_role FROM users",String.class));
        String hash=jdbc.queryForObject("SELECT password_hash FROM users",String.class);
        assertTrue(new com.bodego.platform.iam.infrastructure.hashing.BCryptPasswordHashingService().matches(
                "Fixture-password-2026",new com.bodego.platform.iam.domain.model.valueobjects.PasswordHash(hash)));
        assertThrows(IllegalStateException.class,()->provision("other@example.invalid"));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM businesses",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM users",Integer.class));
    }
    @Test void invalidAdminRollsBackTheBusiness() throws Exception {
        migrate();assertThrows(RuntimeException.class,()->provision("invalid-email"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM businesses",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM users",Integer.class));
    }
    @Test void concurrentBootstrapCreatesExactlyOnePair() throws Exception {
        migrate();var start=new CountDownLatch(1);var success=new java.util.concurrent.atomic.AtomicInteger();
        try(var executor=Executors.newFixedThreadPool(2)) {
            var futures=new ArrayList<Future<?>>();
            for(int i=0;i<2;i++) futures.add(executor.submit(()->{
                try {start.await();provision("fixture@example.invalid");success.incrementAndGet();}
                catch(IllegalStateException expected) {assertEquals("STAGING_NOT_FRESH_OR_NOT_MIGRATED",expected.getMessage());}
                catch(InterruptedException failure) {throw new AssertionError(failure);}
            }));
            start.countDown();for(var future:futures) future.get(30,TimeUnit.SECONDS);
        }
        assertEquals(1,success.get());assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM users",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM businesses",Integer.class));
    }
}
