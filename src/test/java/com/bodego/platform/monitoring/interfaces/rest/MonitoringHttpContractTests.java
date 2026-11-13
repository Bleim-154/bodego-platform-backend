package com.bodego.platform.monitoring.interfaces.rest;

import com.bodego.platform.catalog.domain.model.aggregates.Category;
import com.bodego.platform.catalog.domain.model.aggregates.Product;
import com.bodego.platform.catalog.infrastructure.persistence.jpa.repositories.CategoryRepository;
import com.bodego.platform.catalog.infrastructure.persistence.jpa.repositories.ProductRepository;
import com.bodego.platform.inventory.domain.model.aggregates.Lot;
import com.bodego.platform.inventory.infrastructure.persistence.jpa.repositories.LotRepository;
import com.bodego.platform.iam.application.internal.outboundservices.tokens.AccessTokenService;
import com.bodego.platform.iam.domain.model.aggregates.Business;
import com.bodego.platform.iam.domain.model.aggregates.User;
import com.bodego.platform.iam.domain.model.valueobjects.EmailAddress;
import com.bodego.platform.iam.domain.model.valueobjects.UserRole;
import com.bodego.platform.iam.infrastructure.hashing.BCryptPasswordHashingService;
import com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.BusinessRepository;
import com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import com.bodego.platform.monitoring.domain.model.aggregates.Alert;
import com.bodego.platform.monitoring.domain.model.aggregates.AuditLog;
import com.bodego.platform.monitoring.domain.model.valueobjects.AlertType;
import com.bodego.platform.monitoring.domain.model.valueobjects.AlertSeverity;
import com.bodego.platform.monitoring.infrastructure.persistence.jpa.repositories.AlertRepository;
import com.bodego.platform.monitoring.infrastructure.persistence.jpa.repositories.AuditLogRepository;
import com.bodego.platform.shared.application.security.AuthenticatedIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MonitoringHttpContractTests {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("bodego_monitoring_contract_isolated").withStartupTimeout(Duration.ofMinutes(2));
    static final String KEY = Base64.getEncoder().encodeToString(java.security.SecureRandom.getSeed(32));
    @DynamicPropertySource static void isolated(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("bodego.security.jwt.secret-base64", () -> KEY);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired BusinessRepository businesses;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired LotRepository lots;
    @Autowired AlertRepository alerts;
    @Autowired AuditLogRepository audits;
    @Autowired AccessTokenService tokens;
    Long business, foreignBusiness, product, foreignProduct, adminId, employeeId;
    String admin, employee;

    @BeforeEach void fixtures() {
        assertEquals("bodego_monitoring_contract_isolated", jdbc.queryForObject("select current_database()", String.class));
        assertNotEquals(5432, POSTGRES.getMappedPort(5432));
        for (String table : List.of("audit_logs", "alerts", "inventory_movements", "lots", "products", "categories", "users", "businesses")) jdbc.update("delete from " + table);
        business = businesses.saveAndFlush(new Business("Fixture A", "Fixture address", "123")).getId();
        foreignBusiness = businesses.saveAndFlush(new Business("Fixture B", "Fixture address", "456")).getId();
        var hash = new BCryptPasswordHashingService().hash("Fixture-password-only");
        var a = users.saveAndFlush(new User(business, "Fixture admin", new EmailAddress("admin@example.invalid"), hash, UserRole.ADMIN));
        var e = users.saveAndFlush(new User(business, "Fixture employee", new EmailAddress("employee@example.invalid"), hash, UserRole.EMPLOYEE));
        adminId = a.getId(); employeeId = e.getId(); admin = bearer(a); employee = bearer(e);
        var ownCategory = categories.saveAndFlush(new Category(business, "Fixture category", null));
        var otherCategory = categories.saveAndFlush(new Category(foreignBusiness, "Foreign category", null));
        product = products.saveAndFlush(new Product(business, ownCategory.getId(), "Fixture product", null, BigDecimal.ONE, 5)).getId();
        foreignProduct = products.saveAndFlush(new Product(foreignBusiness, otherCategory.getId(), "Foreign product", null, BigDecimal.ONE, 5)).getId();
    }
    String bearer(User user) { return "Bearer " + tokens.issue(new AuthenticatedIdentity(user.getId(), user.getBusinessId(), user.getRole().name())).accessToken(); }
    Object field(MvcResult result, String path) throws Exception { return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path); }
    Long lot(LocalDate expiration, String code) { return lots.saveAndFlush(new Lot(business, product, code, 0, expiration)).getId(); }
    MvcResult generate() throws Exception {
        return mvc.perform(post("/api/v1/alerts/generate").header("Authorization", admin).contentType("application/json")
                .content("{\"businessId\":" + business + "}")).andExpect(status().isOk()).andReturn();
    }
    int rows(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }

    @Test void generationUsesUtcSevenDayWindowIncludesZeroStockLotsAndDeduplicates() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        lot(today.minusDays(1), "EXPIRED"); lot(today, "TODAY"); lot(today.plusDays(7), "SEVEN"); lot(today.plusDays(8), "EIGHT");
        var generated = generate();
        List<?> types = (List<?>) field(generated, "$[*].type");
        assertEquals(4, types.size()); assertEquals(1, types.stream().filter("EXPIRED"::equals).count());
        assertEquals(2, types.stream().filter("EXPIRING_SOON"::equals).count()); assertEquals(1, types.stream().filter("LOW_STOCK"::equals).count());
        assertEquals(4, rows("alerts")); assertEquals(0, rows("audit_logs")); assertEquals(0, rows("inventory_movements"));
        assertEquals(0, ((List<?>)field(generate(), "$")).size());
        mvc.perform(get("/api/v1/alerts").header("Authorization", employee).param("productId", product.toString()).param("resolved", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        assertEquals(4, rows("alerts")); assertEquals(0, jdbc.queryForObject("select count(*) from alerts where business_id=?", Integer.class, foreignBusiness));
    }

    @Test void resolvingIsBodylessIdempotentAndDoesNotCreateClientStyleAudit() throws Exception {
        Long lot = lot(LocalDate.now(ZoneOffset.UTC).plusDays(7), "SEVEN");
        var result = generate(); Long alert = ((Number)field(result, "$[0].id")).longValue();
        var first = mvc.perform(patch("/api/v1/alerts/{id}/resolve", alert).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resolved").value(true)).andReturn();
        var persisted = jdbc.queryForObject("select resolved_at from alerts where id=?",
                (row, index) -> row.getTimestamp(1).toInstant(), alert);
        var second = mvc.perform(patch("/api/v1/alerts/{id}/resolve", alert).header("Authorization", admin))
                .andExpect(status().isOk()).andReturn();
        assertEquals(persisted, jdbc.queryForObject("select resolved_at from alerts where id=?",
                (row, index) -> row.getTimestamp(1).toInstant(), alert));
        assertEquals(persisted, java.time.Instant.parse((String)field(second, "$.resolvedAt")));
        // The first DTO still holds Java nanoseconds; PostgreSQL stores microseconds.
        // Verify only that conversion tolerance here; idempotency above is exact in storage.
        assertTrue(Math.abs(Duration.between(java.time.Instant.parse((String)field(first, "$.resolvedAt")), persisted).toNanos()) < 1_000);
        mvc.perform(get("/api/v1/alerts").header("Authorization", admin).param("resolved", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        assertEquals(0, rows("audit_logs")); assertEquals(0, rows("inventory_movements"));
        assertEquals(0, lots.findById(lot).orElseThrow().getQuantity());
        assertEquals(1, ((List<?>)field(generate(), "$")).size());
    }

    @Test void rolesForeignReferencesAndUnsupportedWritersRemainDenied() throws Exception {
        Long foreign = alerts.saveAndFlush(new Alert(foreignBusiness, foreignProduct, null, AlertType.LOW_STOCK, AlertSeverity.LOW, "Foreign fixture")).getId();
        mvc.perform(get("/api/v1/alerts/{id}", foreign).header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/alerts/{id}/resolve", foreign).header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/alerts/generate").header("Authorization", admin).contentType("application/json")
                .content("{\"businessId\":" + foreignBusiness + "}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/alerts/generate").header("Authorization", employee).contentType("application/json")
                .content("{\"businessId\":" + business + "}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/alerts/{id}/resolve", foreign).header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/alerts").header("Authorization", admin).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/audit-logs").header("Authorization", admin).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/alerts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", admin).param("businessId", foreignBusiness.toString())).andExpect(status().isForbidden());
        assertEquals(1, rows("alerts")); assertFalse(alerts.findById(foreign).orElseThrow().getResolved()); assertEquals(0, rows("audit_logs"));
    }

    @Test void auditReadsPreserveActualMovementActorHistoricalNullAndTenantWithoutWriting() throws Exception {
        Long lot = lot(null, "MOVEMENT");
        var movement = mvc.perform(post("/api/v1/inventory-movements").header("Authorization", employee).contentType("application/json")
                .content("{\"businessId\":" + business + ",\"lotId\":" + lot + ",\"type\":\"ENTRY\",\"quantity\":3,\"reason\":\"Fixture\"}"))
                .andExpect(status().isCreated()).andReturn();
        Long entityId = ((Number)field(movement, "$.id")).longValue();
        var historical = audits.saveAndFlush(new AuditLog(business, null, "CUSTOM_HISTORICAL_ACTION", "Historical", 1L, "Fixture"));
        var foreign = audits.saveAndFlush(new AuditLog(foreignBusiness, null, "FOREIGN_FIXTURE", "Historical", 1L, "Fixture"));
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", admin).param("entityType", "InventoryMovement").param("entityId", entityId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(employeeId)).andExpect(jsonPath("$[0].occurredAt").isString());
        mvc.perform(get("/api/v1/audit-logs/{id}", historical.getId()).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.action").value("CUSTOM_HISTORICAL_ACTION"));
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", admin)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/v1/audit-logs/{id}", foreign.getId()).header("Authorization", admin)).andExpect(status().isNotFound());
        assertNotEquals(adminId, employeeId); assertEquals(3, rows("audit_logs")); assertEquals(1, rows("inventory_movements"));
    }
}
