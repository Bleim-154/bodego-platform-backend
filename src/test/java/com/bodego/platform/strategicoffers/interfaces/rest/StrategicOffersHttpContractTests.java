package com.bodego.platform.strategicoffers.interfaces.rest;

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
class StrategicOffersHttpContractTests {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("bodego_strategic_offers_contract_isolated").withStartupTimeout(Duration.ofMinutes(2));
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
        assertEquals("bodego_strategic_offers_contract_isolated", jdbc.queryForObject("select current_database()", String.class));
        assertNotEquals(5432, POSTGRES.getMappedPort(5432));
        for (String table : List.of("audit_logs", "recommendations", "offers", "alerts", "inventory_movements", "lots", "products", "categories", "users", "businesses")) jdbc.update("delete from " + table);
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
    int rows(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }

    MvcResult generateRecommendations() throws Exception {
        return mvc.perform(post("/api/v1/recommendations/generate").header("Authorization",admin).contentType("application/json")
                .content("{\"businessId\":"+business+"}")).andExpect(status().isOk()).andReturn();
    }
    void entry(Long lot, int quantity) throws Exception {
        mvc.perform(post("/api/v1/inventory-movements").header("Authorization",employee).contentType("application/json")
                .content("{\"businessId\":"+business+",\"lotId\":"+lot+",\"type\":\"ENTRY\",\"quantity\":"+quantity+",\"reason\":\"Fixture\"}"))
                .andExpect(status().isCreated());
    }
    String information(String title,String discount) {
        return "{\"title\":\""+title+"\",\"discountPercentage\":"+discount+",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-12-31\"}";
    }
    String createBody(Long businessId,Long productId,String discount) {
        return "{\"businessId\":"+businessId+",\"productId\":"+productId+","+information("Fixture",discount).substring(1);
    }
    @Test void generationUsesActiveProductsUtcThirtyDaysAndPositiveLotsWithoutCreatingOffers() throws Exception {
        LocalDate today=LocalDate.now(ZoneOffset.UTC);
        entry(lot(today.plusDays(30),"THIRTY"),1); entry(lot(today.plusDays(31),"THIRTY_ONE"),1);
        entry(lot(today.minusDays(1),"EXPIRED"),1); lot(today,"ZERO");
        var generated=generateRecommendations(); List<?> types=(List<?>)field(generated,"$[*].type");
        assertEquals(2,types.size()); assertTrue(types.contains("EXPIRING")); assertTrue(types.contains("RESTOCK"));
        assertEquals(2,rows("recommendations")); assertEquals(0,rows("offers")); assertEquals(5,rows("audit_logs"));
        assertEquals(0,((List<?>)field(generateRecommendations(),"$")).size()); assertEquals(5,rows("audit_logs"));
        mvc.perform(patch("/api/v1/products/{id}/deactivate",product).header("Authorization",admin)).andExpect(status().isOk());
        assertEquals(0,((List<?>)field(generateRecommendations(),"$")).size());
        mvc.perform(get("/api/v1/recommendations").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }
    @Test void overstockDismissalIsIdempotentAuditedOnceAndGenerationReturnsOnlyNewRows() throws Exception {
        entry(lot(null,"STOCK"),16); var generated=generateRecommendations();
        assertEquals(List.of("OVERSTOCK"),field(generated,"$[*].type")); Long id=((Number)field(generated,"$[0].id")).longValue();
        int before=rows("audit_logs");
        for(int i=0;i<2;i++) mvc.perform(patch("/api/v1/recommendations/{id}/dismiss",id).header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dismissed").value(true));
        assertEquals(before+1,rows("audit_logs")); assertEquals(1,((List<?>)field(generateRecommendations(),"$")).size());
        assertEquals(2,rows("recommendations")); assertEquals(0,rows("offers"));
        mvc.perform(get("/api/v1/recommendations").header("Authorization",admin).param("productId",product.toString()).param("type","OVERSTOCK").param("dismissed","true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }
    @Test void offersSupportDecimalsImmutableProductInactiveMetadataAndIdempotentTransitions() throws Exception {
        var created=mvc.perform(post("/api/v1/offers").header("Authorization",admin).contentType("application/json").content(createBody(business,product,"0.5")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.discountPercentage").value(0.5)).andReturn();
        Long id=((Number)field(created,"$.id")).longValue(); assertEquals(1,rows("audit_logs"));
        for(int i=0;i<2;i++) mvc.perform(patch("/api/v1/offers/{id}/deactivate",id).header("Authorization",admin)).andExpect(status().isOk());
        assertEquals(2,rows("audit_logs"));
        mvc.perform(patch("/api/v1/products/{id}/deactivate",product).header("Authorization",admin)).andExpect(status().isOk());
        int before=rows("audit_logs");
        for(int i=0;i<2;i++) mvc.perform(put("/api/v1/offers/{id}",id).header("Authorization",admin).contentType("application/json").content(information("Edited","99.9")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.productId").value(product)).andExpect(jsonPath("$.active").value(false));
        assertEquals(before+1,rows("audit_logs"));
        mvc.perform(patch("/api/v1/offers/{id}/activate",id).header("Authorization",admin)).andExpect(status().isBadRequest());
        assertEquals(before+1,rows("audit_logs"));
        mvc.perform(get("/api/v1/offers/{id}",id).header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(patch("/api/v1/products/{id}/activate",product).header("Authorization",admin)).andExpect(status().isOk());
        for(int i=0;i<2;i++) mvc.perform(patch("/api/v1/offers/{id}/activate",id).header("Authorization",admin)).andExpect(status().isOk());
        assertEquals(before+2,rows("audit_logs"));
        assertEquals(adminId,jdbc.queryForObject("select user_id from audit_logs where action='OFFER_CREATED'",Long.class));
    }
    @Test void employeeForeignReferencesInvalidDiscountsAndUnknownWritersCannotModifyData() throws Exception {
        mvc.perform(get("/api/v1/offers").header("Authorization",employee)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/recommendations").header("Authorization",employee)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/recommendations/generate").header("Authorization",employee).contentType("application/json").content("{\"businessId\":"+business+"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/offers").header("Authorization",employee).contentType("application/json").content(createBody(business,product,"10"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/offers").header("Authorization",admin).contentType("application/json").content(createBody(business,foreignProduct,"10"))).andExpect(status().isBadRequest());
        for(String discount:List.of("0","100","-1")) mvc.perform(post("/api/v1/offers").header("Authorization",admin).contentType("application/json")
                .content(createBody(business,product,discount))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/recommendations").header("Authorization",admin).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/offers").header("Authorization",admin).param("businessId",foreignBusiness.toString())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/recommendations")).andExpect(status().isUnauthorized());
        assertEquals(0,rows("offers")); assertEquals(0,rows("recommendations")); assertEquals(0,rows("audit_logs"));
    }
}
