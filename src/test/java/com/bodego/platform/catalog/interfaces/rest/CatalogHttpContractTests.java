package com.bodego.platform.catalog.interfaces.rest;

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

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP/security/JPA contracts on an ephemeral database only; never the local database. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogHttpContractTests {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("bodego_catalog_contract_isolated").withStartupTimeout(Duration.ofMinutes(2));
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
    @Autowired AccessTokenService tokens;
    Long business, foreignBusiness;
    String admin, employee, foreignAdmin;

    @BeforeEach void fixtures() {
        assertEquals("bodego_catalog_contract_isolated", jdbc.queryForObject("select current_database()", String.class));
        assertNotEquals(5432, POSTGRES.getMappedPort(5432));
        for (String table : List.of("products", "categories", "users", "businesses")) jdbc.update("delete from " + table);
        business = businesses.saveAndFlush(new Business("Fixture A", "Fixture address", "123")).getId();
        foreignBusiness = businesses.saveAndFlush(new Business("Fixture B", "Fixture address", "456")).getId();
        var hash = new BCryptPasswordHashingService().hash("Fixture-password-only");
        var adminUser = users.saveAndFlush(new User(business, "Fixture admin", new EmailAddress("admin@example.invalid"), hash, UserRole.ADMIN));
        var employeeUser = users.saveAndFlush(new User(business, "Fixture employee", new EmailAddress("employee@example.invalid"), hash, UserRole.EMPLOYEE));
        var foreignUser = users.saveAndFlush(new User(foreignBusiness, "Foreign fixture", new EmailAddress("foreign@example.invalid"), hash, UserRole.ADMIN));
        admin = bearer(adminUser);
        employee = bearer(employeeUser);
        foreignAdmin = bearer(foreignUser);
    }

    String bearer(User user) {
        return "Bearer " + tokens.issue(new AuthenticatedIdentity(user.getId(), user.getBusinessId(), user.getRole().name())).accessToken();
    }
    Long id(MvcResult result) throws Exception {
        return ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }
    Long category(String token, Long tenant, String name) throws Exception {
        return id(mvc.perform(post("/api/v1/categories").header("Authorization", token).contentType("application/json")
                .content("{\"businessId\":" + tenant + ",\"name\":\"" + name + "\",\"description\":null}"))
                .andExpect(status().isCreated()).andReturn());
    }
    String productBody(Long tenant, Long category) {
        return "{\"businessId\":" + tenant + ",\"categoryId\":" + category
                + ",\"name\":\"Fixture product\",\"description\":null,\"price\":12.34,\"minimumStock\":2}";
    }

    @Test void categoryAndProductContractsSupportTheAngularPayloads() throws Exception {
        Long category = category(admin, business, "Fixture category");
        mvc.perform(get("/api/v1/categories/{id}", category).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(category))
                .andExpect(jsonPath("$.businessId").value(business));
        mvc.perform(put("/api/v1/categories/{id}", category).header("Authorization", admin).contentType("application/json")
                .content("{\"name\":\"Edited category\",\"description\":\"Description\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Edited category"));
        Long product = id(mvc.perform(post("/api/v1/products").header("Authorization", admin).contentType("application/json")
                        .content(productBody(business, category)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.price").value(12.34)).andExpect(jsonPath("$.categoryId").value(category)).andReturn());
        mvc.perform(put("/api/v1/products/{id}", product).header("Authorization", admin).contentType("application/json")
                .content("{\"categoryId\":" + category + ",\"name\":\"Edited product\",\"description\":null,\"price\":20.50,\"minimumStock\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Edited product"));
        mvc.perform(patch("/api/v1/products/{id}/deactivate", product).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(patch("/api/v1/products/{id}/activate", product).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
    }

    @Test void listsAreUnpaginatedAndFilterOnlyWithinTheAuthenticatedBusiness() throws Exception {
        Long own = category(admin, business, "Own fixture");
        Long foreign = category(foreignAdmin, foreignBusiness, "Foreign fixture");
        mvc.perform(post("/api/v1/products").header("Authorization", admin).contentType("application/json").content(productBody(business, own)))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/categories").header("Authorization", employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/products").header("Authorization", employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/products").param("categoryId", own.toString()).header("Authorization", employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/products").param("categoryId", foreign.toString()).header("Authorization", employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/products").param("businessId", business.toString()).param("categoryId", own.toString()).header("Authorization", admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/categories").param("businessId", foreignBusiness.toString()).header("Authorization", admin))
                .andExpect(status().isForbidden());
    }

    @Test void roleAndTenantChecksRejectWritesAndReferencesWithoutPartialChanges() throws Exception {
        Long own = category(admin, business, "Own fixture");
        Long foreign = category(foreignAdmin, foreignBusiness, "Foreign fixture");
        mvc.perform(post("/api/v1/products").header("Authorization", employee).contentType("application/json").content(productBody(business, own)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/categories/{id}", own).header("Authorization", employee).contentType("application/json")
                .content("{\"name\":\"Denied\",\"description\":null}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/products").header("Authorization", admin).contentType("application/json").content(productBody(business, foreign)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/products").header("Authorization", admin).contentType("application/json").content(productBody(foreignBusiness, own)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/categories/{id}", foreign).header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/categories/{id}", Long.MAX_VALUE).header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/categories/{id}", own).header("Authorization", admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
        assertEquals(0L, jdbc.queryForObject("select count(*) from products", Long.class));
        assertEquals(2L, jdbc.queryForObject("select count(*) from categories", Long.class));
    }
}
