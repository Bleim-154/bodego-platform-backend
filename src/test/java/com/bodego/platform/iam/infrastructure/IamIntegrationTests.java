package com.bodego.platform.iam.infrastructure;

import com.bodego.platform.iam.application.internal.outboundservices.hashing.PasswordHashingService;
import com.bodego.platform.iam.domain.exceptions.*;
import com.bodego.platform.iam.domain.model.aggregates.User;
import com.bodego.platform.iam.domain.model.commands.*;
import com.bodego.platform.iam.domain.model.queries.*;
import com.bodego.platform.iam.domain.model.valueobjects.*;
import com.bodego.platform.iam.domain.services.*;
import com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.*;
import com.bodego.platform.iam.interfaces.acl.IamContextFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class IamIntegrationTests {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("bodego_iam_isolated");

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("bodego.security.jwt.secret-base64", () -> java.util.Base64.getEncoder().encodeToString(java.security.SecureRandom.getSeed(32)));
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired BusinessCommandService businessCommands;
    @Autowired BusinessQueryService businessQueries;
    @Autowired UserCommandService userCommands;
    @Autowired UserQueryService userQueries;
    @Autowired BusinessRepository businesses;
    @Autowired UserRepository users;
    @Autowired IamContextFacade facade;
    @Autowired PasswordHashingService hashing;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    private static char[] password() { return "Fixture-password-2026".toCharArray(); }
    private static String email() { return "fixture-" + UUID.randomUUID() + "@example.invalid"; }

    private Long business() {
        return businessCommands.handle(new CreateBusinessCommand("Fixture shop", "Fixture address", "+51 123456"));
    }

    private Long employee(Long businessId, String email) {
        return userCommands.handle(new CreateEmployeeCommand(businessId, "Fixture employee", email, password()));
    }

    private void deactivateBusiness(Long businessId) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var business = businesses.findWithLockById(businessId).orElseThrow();
            business.deactivate();
            businesses.save(business);
        });
    }

    @Test
    void containerMigratesCurrentSchemaAndHibernateValidatesIamMappings() {
        assertTrue(jdbc.queryForObject("SHOW server_version", String.class).startsWith("18.6"));
        assertNotEquals("bodego_db", jdbc.queryForObject("SELECT current_database()", String.class));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success AND type='SQL'", Integer.class));
        assertEquals(List.of("1", "2", "4"), jdbc.queryForList("SELECT version FROM flyway_schema_history ORDER BY installed_rank", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='3'", Integer.class));
    }

    @Test
    void createsUpdatesAndAuditsBusinessWithoutEmail() {
        Long id = business();
        var original = businessQueries.handle(new GetBusinessByIdQuery(id)).orElseThrow();
        assertNotNull(original.getCreatedAt());
        assertNotNull(original.getUpdatedAt());
        var updated = businessCommands.handle(new UpdateBusinessCommand(id, " Changed ", " New address ", " +51 987 ")).orElseThrow();
        assertEquals("Changed", updated.getName());
        assertEquals("New address", updated.getAddress());
        assertEquals("+51 987", updated.getPhone());
        assertEquals(original.getCreatedAt(), updated.getCreatedAt());
        assertTrue(businessQueries.handle(new IsBusinessActiveQuery(id)));
        assertFalse(businessQueries.handle(new IsBusinessActiveQuery(Long.MAX_VALUE)));
        assertTrue(businessCommands.handle(new UpdateBusinessCommand(Long.MAX_VALUE, "Name", "Address", "123")).isEmpty());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='businesses' AND column_name='email'", Integer.class));
    }

    @Test
    void employeePersistsOnlyBcryptAndQueriesNormalizeAndScopeEmail() {
        Long id = business();
        String email = email();
        var command = new CreateEmployeeCommand(id, " Employee ", " " + email.toUpperCase(java.util.Locale.ROOT) + " ", password());
        Long userId = userCommands.handle(command);
        assertArrayEquals(new char[password().length], command.password());
        var user = userQueries.handle(new GetUserByIdQuery(id, userId)).orElseThrow();
        assertEquals(UserRole.EMPLOYEE, user.getRole());
        assertEquals(email, user.getEmail());
        assertNotNull(user.getCreatedAt());
        String stored = jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?", String.class, userId);
        assertTrue(hashing.matches(new String(password()), new PasswordHash(stored)));
        assertNotEquals(new String(password()), stored);
        assertEquals(userId, userQueries.handle(new GetUserByEmailQuery(id, " " + email.toUpperCase(java.util.Locale.ROOT) + " ")).orElseThrow().getId());
        Long other = business();
        assertTrue(userQueries.handle(new GetUserByIdQuery(other, userId)).isEmpty());
        assertTrue(userQueries.handle(new GetUserByEmailQuery(other, email)).isEmpty());
        assertTrue(facade.getUserById(other, userId).isEmpty());
        assertTrue(facade.getUserById(id, userId).orElseThrow().enabled());
    }

    @Test
    void globalEmailUniquenessSpansBusinessesAndInactiveUsers() {
        Long first = business();
        Long second = business();
        String email = email();
        Long userId = employee(first, email);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var user = users.findByIdAndBusinessId(userId, first).orElseThrow();
            user.deactivate();
            users.save(user);
        });
        assertThrows(EmailAlreadyExistsException.class, () -> employee(second, " " + email.toUpperCase(java.util.Locale.ROOT) + " "));
        assertFalse(facade.getUserById(first, userId).orElseThrow().enabled());
    }

    @Test
    void rejectsMissingAndInactiveBusinessesAndErasesRejectedCommand() {
        var missing = new CreateEmployeeCommand(Long.MAX_VALUE, "Name", email(), password());
        assertThrows(BusinessNotFoundException.class, () -> userCommands.handle(missing));
        assertArrayEquals(new char[password().length], missing.password());
        Long id = business();
        deactivateBusiness(id);
        assertFalse(businessQueries.handle(new IsBusinessActiveQuery(id)));
        assertThrows(InactiveBusinessException.class, () -> employee(id, email()));
        assertThrows(InactiveBusinessException.class, () -> userCommands.handle(new ProvisionFirstAdminCommand(id, "Admin", email(), password())));
    }

    @Test
    void inactiveBusinessDisablesOtherwiseActiveUserWithoutChangingUserFlag() {
        Long id = business();
        Long userId = employee(id, email());
        deactivateBusiness(id);
        var snapshot = facade.getUserById(id, userId).orElseThrow();
        assertTrue(snapshot.userActive());
        assertFalse(snapshot.businessActive());
        assertFalse(snapshot.enabled());
    }

    @Test
    void provisionsExactlyOneFirstAdminPerBusinessIncludingInactiveAdmins() {
        Long first = business();
        Long second = business();
        Long admin = userCommands.handle(new ProvisionFirstAdminCommand(first, "Fixture admin", email(), password()));
        assertEquals(UserRole.ADMIN, userQueries.handle(new GetUserByIdQuery(first, admin)).orElseThrow().getRole());
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var user = users.findByIdAndBusinessId(admin, first).orElseThrow();
            user.deactivate();
            users.save(user);
        });
        assertThrows(FirstAdminAlreadyProvisionedException.class, () -> userCommands.handle(new ProvisionFirstAdminCommand(first, "Another", email(), password())));
        assertNotNull(userCommands.handle(new ProvisionFirstAdminCommand(second, "Fixture admin", email(), password())));
    }

    @Test
    void concurrentProvisioningSerializesOnBusinessAcrossTransactions() throws Exception {
        Long id = business();
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Object> attempt = () -> {
                start.await(20, TimeUnit.SECONDS);
                try {
                    return userCommands.handle(new ProvisionFirstAdminCommand(id, "Concurrent admin", email(), password()));
                } catch (FirstAdminAlreadyProvisionedException exception) {
                    return exception;
                }
            };
            var a = executor.submit(attempt);
            var b = executor.submit(attempt);
            var results = List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(Long.class::isInstance).count());
            assertEquals(1, results.stream().filter(FirstAdminAlreadyProvisionedException.class::isInstance).count());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users WHERE business_id=? AND user_role='ADMIN'", Integer.class, id));
        }
    }

    @Test
    void databaseUniqueConstraintTranslatesConcurrentGlobalEmailConflict() throws Exception {
        Long first = business();
        Long second = business();
        EmailAddress email = new EmailAddress(email());
        PasswordHash hash = hashing.hash(new String(password()));
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var attempts = List.of(first, second).stream().map(id -> executor.submit((Callable<Object>) () -> {
                start.await(20, TimeUnit.SECONDS);
                try {
                    return new TransactionTemplate(transactions).execute(status ->
                            users.persist(new User(id, "Concurrent employee", email, hash, UserRole.EMPLOYEE)).getId());
                } catch (EmailAlreadyExistsException exception) {
                    return exception;
                }
            })).toList();
            var results = List.of(attempts.get(0).get(30, TimeUnit.SECONDS), attempts.get(1).get(30, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(Long.class::isInstance).count());
            assertEquals(1, results.stream().filter(EmailAlreadyExistsException.class::isInstance).count());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users WHERE email=?", Integer.class, email.value()));
        }
    }
}
