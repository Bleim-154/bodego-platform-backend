package com.bodego.platform.inventory.interfaces.rest;

import com.bodego.platform.catalog.domain.model.aggregates.Category;
import com.bodego.platform.catalog.domain.model.aggregates.Product;
import com.bodego.platform.catalog.infrastructure.persistence.jpa.repositories.CategoryRepository;
import com.bodego.platform.catalog.infrastructure.persistence.jpa.repositories.ProductRepository;
import com.bodego.platform.iam.application.internal.outboundservices.tokens.AccessTokenService;
import com.bodego.platform.iam.domain.model.aggregates.Business;
import com.bodego.platform.iam.domain.model.aggregates.User;
import com.bodego.platform.iam.domain.model.valueobjects.EmailAddress;
import com.bodego.platform.iam.domain.model.valueobjects.UserRole;
import com.bodego.platform.iam.infrastructure.hashing.BCryptPasswordHashingService;
import com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.BusinessRepository;
import com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.UserRepository;
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
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InventoryHttpContractTests {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("bodego_inventory_contract_isolated").withStartupTimeout(Duration.ofMinutes(2));
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
    @Autowired AccessTokenService tokens;
    Long business, foreignBusiness, product, foreignProduct, employeeId;
    String admin, employee, foreignAdmin;

    @BeforeEach void fixtures() {
        assertEquals("bodego_inventory_contract_isolated", jdbc.queryForObject("select current_database()", String.class));
        assertNotEquals(5432, POSTGRES.getMappedPort(5432));
        for (String table : List.of("audit_logs", "inventory_movements", "lots", "products", "categories", "users", "businesses")) {
            jdbc.update("delete from " + table);
        }
        business = businesses.saveAndFlush(new Business("Fixture A", "Fixture address", "123")).getId();
        foreignBusiness = businesses.saveAndFlush(new Business("Fixture B", "Fixture address", "456")).getId();
        var hash = new BCryptPasswordHashingService().hash("Fixture-password-only");
        var a = users.saveAndFlush(new User(business, "Fixture admin", new EmailAddress("admin@example.invalid"), hash, UserRole.ADMIN));
        var e = users.saveAndFlush(new User(business, "Fixture employee", new EmailAddress("employee@example.invalid"), hash, UserRole.EMPLOYEE));
        var f = users.saveAndFlush(new User(foreignBusiness, "Foreign fixture", new EmailAddress("foreign@example.invalid"), hash, UserRole.ADMIN));
        employeeId = e.getId(); admin = bearer(a); employee = bearer(e); foreignAdmin = bearer(f);
        var ownCategory = categories.saveAndFlush(new Category(business, "Fixture category", null));
        var otherCategory = categories.saveAndFlush(new Category(foreignBusiness, "Foreign category", null));
        product = products.saveAndFlush(new Product(business, ownCategory.getId(), "Fixture product", null, BigDecimal.ONE, 0)).getId();
        foreignProduct = products.saveAndFlush(new Product(foreignBusiness, otherCategory.getId(), "Foreign product", null, BigDecimal.ONE, 0)).getId();
    }
    String bearer(User user) {
        return "Bearer " + tokens.issue(new AuthenticatedIdentity(user.getId(), user.getBusinessId(), user.getRole().name())).accessToken();
    }
    Long id(MvcResult result) throws Exception {
        return ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }
    String lotBody(Long tenant, Long productId, int quantity) {
        return "{\"businessId\":" + tenant + ",\"productId\":" + productId + ",\"batchNumber\":\"LOT\",\"quantity\":" + quantity + ",\"expirationDate\":null}";
    }
    Long lot(String token, Long tenant, Long productId) throws Exception {
        return id(mvc.perform(post("/api/v1/lots").header("Authorization", token).contentType("application/json")
                .content(lotBody(tenant, productId, 0))).andExpect(status().isCreated()).andReturn());
    }
    String movementBody(Long tenant, Long lot, String type, int quantity) {
        return "{\"businessId\":" + tenant + ",\"lotId\":" + lot + ",\"type\":\"" + type + "\",\"quantity\":" + quantity + ",\"reason\":\"Fixture\"}";
    }
    MvcResult movement(String token, Long tenant, Long lot, String type, int quantity, int expectedStatus) throws Exception {
        return mvc.perform(post("/api/v1/inventory-movements").header("Authorization", token).contentType("application/json")
                .content(movementBody(tenant, lot, type, quantity))).andExpect(status().is(expectedStatus)).andReturn();
    }
    int quantity(Long lot) { return jdbc.queryForObject("select quantity from lots where id=?", Integer.class, lot); }
    void effects(long count) {
        assertEquals(count, jdbc.queryForObject("select count(*) from inventory_movements", Long.class));
        assertEquals(count, jdbc.queryForObject("select count(*) from audit_logs", Long.class));
    }

    @Test void lotContractsSupportZeroStockCreationAndMetadataOnlyUpdates() throws Exception {
        Long lot = lot(admin, business, product);
        mvc.perform(get("/api/v1/lots/{id}", lot).header("Authorization", employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.batchNumber").value("LOT"))
                .andExpect(jsonPath("$.quantity").value(0)).andExpect(jsonPath("$.entryDate").doesNotExist());
        mvc.perform(put("/api/v1/lots/{id}", lot).header("Authorization", admin).contentType("application/json")
                .content("{\"batchNumber\":\"EDITED\",\"expirationDate\":\"2027-01-01\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(0))
                .andExpect(jsonPath("$.productId").value(product)).andExpect(jsonPath("$.expirationDate").value("2027-01-01"));
        mvc.perform(get("/api/v1/lots").param("productId", product.toString()).header("Authorization", employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/lots").param("businessId", business.toString()).param("productId", product.toString()).header("Authorization", admin))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/lots/{id}", lot).header("Authorization", admin).contentType("application/json")
                .content("{\"availableQuantity\":99}")).andExpect(status().isForbidden());
        assertEquals(0, quantity(lot)); effects(0);
    }

    @Test void employeeMovementOwnsStockAuditAndServerTimestampAtomically() throws Exception {
        Long lot = lot(admin, business, product);
        var created = movement(employee, business, lot, "ENTRY", 10, 201);
        Long movementId = id(created);
        mvc.perform(get("/api/v1/inventory-movements/{id}", movementId).header("Authorization", employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("ENTRY"))
                .andExpect(jsonPath("$.lotId").value(lot)).andExpect(jsonPath("$.createdAt").isString());
        assertEquals(10, quantity(lot)); effects(1);
        assertEquals(employeeId, jdbc.queryForObject("select user_id from audit_logs", Long.class));
        assertEquals(business, jdbc.queryForObject("select business_id from audit_logs", Long.class));
        movement(employee, business, lot, "EXIT", 3, 201);
        assertEquals(7, quantity(lot)); effects(2);
        movement(employee, business, lot, "EXIT", 8, 400);
        assertEquals(7, quantity(lot)); effects(2);
        mvc.perform(get("/api/v1/inventory-movements").param("lotId", lot.toString()).header("Authorization", employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test void rolesCrossBusinessReferencesAndInvalidEnumsCannotWrite() throws Exception {
        Long own = lot(admin, business, product), foreign = lot(foreignAdmin, foreignBusiness, foreignProduct);
        mvc.perform(post("/api/v1/lots").header("Authorization", employee).contentType("application/json").content(lotBody(business, product, 0)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/lots/{id}", own).header("Authorization", employee).contentType("application/json")
                .content("{\"batchNumber\":\"Denied\",\"expirationDate\":null}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/lots").header("Authorization", admin).contentType("application/json").content(lotBody(business, foreignProduct, 0)))
                .andExpect(status().isBadRequest());
        movement(employee, foreignBusiness, own, "ENTRY", 1, 403);
        movement(employee, business, foreign, "ENTRY", 1, 400);
        movement(employee, business, Long.MAX_VALUE, "ENTRY", 1, 400);
        movement(employee, business, own, "IN", 1, 400);
        mvc.perform(get("/api/v1/lots/{id}", foreign).header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/lots/{id}", Long.MAX_VALUE).header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/lots").param("businessId", foreignBusiness.toString()).header("Authorization", admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/inventory-movements")).andExpect(status().isUnauthorized());
        assertEquals(0, quantity(own)); assertEquals(0, quantity(foreign)); effects(0);
    }

    @Test void stockIntegerOverflowIsRejectedWithoutExtraMovementOrAudit() throws Exception {
        Long lot = lot(admin, business, product);
        movement(employee, business, lot, "ENTRY", Integer.MAX_VALUE, 201);
        movement(employee, business, lot, "ENTRY", 1, 400);
        assertEquals(Integer.MAX_VALUE, quantity(lot)); effects(1);
    }

    @Test void concurrentExitsSerializeOnTheLotAndCannotOverdrawStock() throws Exception {
        Long lot = lot(admin, business, product);
        movement(employee, business, lot, "ENTRY", 10, 201);
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> exit = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return mvc.perform(post("/api/v1/inventory-movements").header("Authorization", employee).contentType("application/json")
                        .content(movementBody(business, lot, "EXIT", 8))).andReturn().getResponse().getStatus();
            };
            var first = executor.submit(exit); var second = executor.submit(exit);
            var statuses = new ArrayList<>(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)));
            Collections.sort(statuses); assertEquals(List.of(201, 400), statuses);
        }
        assertEquals(2, quantity(lot)); effects(2);
    }
}
