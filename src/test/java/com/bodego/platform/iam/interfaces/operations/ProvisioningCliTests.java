package com.bodego.platform.iam.interfaces.operations;

import com.bodego.platform.iam.domain.exceptions.*;
import com.bodego.platform.iam.domain.model.aggregates.Business;
import com.bodego.platform.iam.domain.model.commands.ProvisionFirstAdminCommand;
import com.bodego.platform.iam.domain.model.valueobjects.PasswordHash;
import com.bodego.platform.iam.domain.services.UserCommandService;
import com.bodego.platform.iam.infrastructure.hashing.BCryptPasswordHashingService;
import com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.BusinessRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class ProvisioningCliTests {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("bodego_operational_provisioning_isolated")
            .withStartupTimeout(java.time.Duration.ofMinutes(2));
    static JdbcTemplate jdbc;
    AnnotationConfigApplicationContext context;
    UserCommandService commands;
    BusinessRepository businesses;
    static String url() { return "jdbc:postgresql://"+POSTGRES.getHost()+":"+POSTGRES.getMappedPort(5432)+"/"+POSTGRES.getDatabaseName(); }
    @BeforeAll static void isolatedSchema() throws Exception {
        assertNotEquals("bodego_db",POSTGRES.getDatabaseName());
        jdbc=new JdbcTemplate(new DriverManagerDataSource(url(),POSTGRES.getUsername(),POSTGRES.getPassword()));
        assertEquals(POSTGRES.getDatabaseName(),jdbc.queryForObject("select current_database()",String.class));
        assertTrue(ProvisionFirstAdminCli.inspect(ProvisionFirstAdminCli.Target.parse(url()).expectSqlPort(5432),POSTGRES.getUsername(),
                POSTGRES.getPassword().toCharArray()).contains("PostgreSQL 18.6"));
        Flyway.configure().dataSource(url(),POSTGRES.getUsername(),POSTGRES.getPassword())
                .locations("classpath:db/migration").baselineOnMigrate(false).cleanDisabled(true).load().migrate();
    }
    @BeforeEach void operationalRuntime() {
        jdbc.update("delete from users"); jdbc.update("delete from businesses");
        context=ProvisioningRuntime.open(url(),POSTGRES.getUsername(),POSTGRES.getPassword().toCharArray());
        commands=context.getBean(UserCommandService.class); businesses=context.getBean(BusinessRepository.class);
    }
    @AfterEach void closeRuntime() {if(context!=null) context.close();}
    Long business() {return businesses.saveAndFlush(new Business("Isolated fixture","Fixture address","12345")).getId();}
    ProvisionFirstAdminCommand command(Long id,String email) {
        return new ProvisionFirstAdminCommand(id,"Isolated fixture admin",email,"Fixture-password-2026".toCharArray());
    }
    @Test void runtimeProvisionsFirstAdminWithBcryptWithoutBootHttpJwtOrFlyway() {
        Long business=business(); Long id=commands.handle(command(business," ADMIN@EXAMPLE.INVALID "));
        assertEquals("admin@example.invalid",jdbc.queryForObject("select email from users where id=?",String.class,id));
        assertEquals("ADMIN",jdbc.queryForObject("select user_role from users where id=?",String.class,id));
        String stored=jdbc.queryForObject("select password_hash from users where id=?",String.class,id);
        assertTrue(stored.startsWith("{bcrypt}$2b$12$"));
        assertTrue(new BCryptPasswordHashingService().matches("Fixture-password-2026",new PasswordHash(stored)));
        assertTrue(context.getBeansOfType(org.springframework.security.web.SecurityFilterChain.class).isEmpty());
        assertTrue(context.getBeansOfType(org.springframework.security.oauth2.jwt.JwtDecoder.class).isEmpty());
        assertTrue(context.getBeansOfType(Flyway.class).isEmpty());
        assertEquals(3,jdbc.queryForObject("select count(*) from flyway_schema_history",Integer.class));
        assertEquals(List.of("1","2","4"),jdbc.queryForList("select version from flyway_schema_history order by installed_rank",String.class));
    }
    @Test void secondProvisioningAlsoRejectsInactiveExistingAdmin() {
        Long business=business(); commands.handle(command(business,"first@example.invalid"));
        jdbc.update("update users set active=false where business_id=?",business);
        assertThrows(FirstAdminAlreadyProvisionedException.class,() -> commands.handle(command(business,"second@example.invalid")));
        assertEquals(1,jdbc.queryForObject("select count(*) from users",Integer.class));
    }
    @Test void missingInactiveBusinessAndGlobalDuplicateAreRejected() {
        assertThrows(BusinessNotFoundException.class,() -> commands.handle(command(Long.MAX_VALUE,"first@example.invalid")));
        Long inactive=business();jdbc.update("update businesses set active=false where id=?",inactive);
        assertThrows(InactiveBusinessException.class,() -> commands.handle(command(inactive,"first@example.invalid")));
        Long first=business(),second=business();commands.handle(command(first,"first@example.invalid"));
        assertThrows(EmailAlreadyExistsException.class,() -> commands.handle(command(second," FIRST@EXAMPLE.INVALID ")));
        assertEquals(1,jdbc.queryForObject("select count(*) from users",Integer.class));
    }
    @Test void concurrentOperationalCallsCreateExactlyOneAdmin() throws Exception {
        Long business=business();var barrier=new CyclicBarrier(2);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Object> action=() -> {
                barrier.await(20,TimeUnit.SECONDS);
                try {return commands.handle(command(business,"admin-"+UUID.randomUUID()+"@example.invalid"));}
                catch(FirstAdminAlreadyProvisionedException expected) {return expected;}
            };
            var first=executor.submit(action);var second=executor.submit(action);
            var results=List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS));
            assertEquals(1,results.stream().filter(Long.class::isInstance).count());
            assertEquals(1,results.stream().filter(FirstAdminAlreadyProvisionedException.class::isInstance).count());
            assertEquals(1,jdbc.queryForObject("select count(*) from users where user_role='ADMIN' and business_id=?",Integer.class,business));
        }
    }
    @Test void explicitTargetAndApprovalRejectUnsafeInputs() throws Exception {
        for(String url:List.of("jdbc:postgresql://localhost/bodego_db","jdbc:postgresql://localhost:5432/postgres",
                "jdbc:postgresql://localhost:5432/bodego_db?password=forbidden","jdbc:postgresql://postgres:secret@localhost:5432/bodego_db",
                "jdbc:postgresql://remote.invalid:5432/bodego_db","jdbc:postgresql://localhost:5432"))
            assertThrows(IllegalArgumentException.class,() -> ProvisionFirstAdminCli.Target.parse(url));
        var target=ProvisionFirstAdminCli.Target.parse(url()).expectSqlPort(5432);
        assertThrows(IllegalArgumentException.class,() -> target.authorize(1L,"yes"));
        assertThrows(IllegalArgumentException.class,() -> target.authorize(2L,target.confirmation(1L)));
        assertDoesNotThrow(() -> target.authorize(1L,target.confirmation(1L)));
        var mismatch=new ProvisionFirstAdminCli.Target(url(),target.host(),target.port(),"wrong_database",5432);
        assertThrows(java.sql.SQLException.class,() -> ProvisionFirstAdminCli.inspect(mismatch,POSTGRES.getUsername(),POSTGRES.getPassword().toCharArray()));
        assertThrows(java.sql.SQLException.class,() -> ProvisionFirstAdminCli.inspect(target.expectSqlPort(5433),POSTGRES.getUsername(),POSTGRES.getPassword().toCharArray()));
    }
    @Test void cliWithoutInteractiveConsoleRejectsBeforeConnectingAndDoesNotEchoArguments() throws Exception {
        String javaExecutable=java.nio.file.Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        String classpath=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        var process=new ProcessBuilder(javaExecutable,"-cp",classpath,ProvisionFirstAdminCli.class.getName(),"do-not-echo-this-input")
                .redirectErrorStream(true).start();
        assertTrue(process.waitFor(20,TimeUnit.SECONDS));
        String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(1,process.exitValue());assertTrue(output.contains("Interactive terminal required"));
        assertFalse(output.contains("do-not-echo-this-input"));
    }
}
