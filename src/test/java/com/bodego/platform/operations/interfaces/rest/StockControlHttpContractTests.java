package com.bodego.platform.operations.interfaces.rest;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class StockControlHttpContractTests {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("bodego_stock_control_contract_isolated").withStartupTimeout(Duration.ofMinutes(2));
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
        assertEquals("bodego_stock_control_contract_isolated", jdbc.queryForObject("select current_database()", String.class));
        assertNotEquals(5432, POSTGRES.getMappedPort(5432));
        assertEquals("180006", jdbc.queryForObject("show server_version_num", String.class));
        for (String table : List.of("audit_logs", "stock_adjustments", "correction_requests", "physical_counts", "wastes", "inventory_movements", "lots", "products", "categories", "users", "businesses")) {
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

    MvcResult sendPost(String path, String token, String body, int status) throws Exception {
        return mvc.perform(post(path).header("Authorization", token).contentType("application/json").content(body))
                .andExpect(status().is(status)).andReturn();
    }
    Long count(Long lot, int quantity) throws Exception {
        return id(sendPost("/api/v1/physical-counts", employee,
                "{\"businessId\":"+business+",\"lotId\":"+lot+",\"countedQuantity\":"+quantity+",\"observation\":\"Fixture\"}",201));
    }
    Long request(Long count, String reason) throws Exception {
        return id(sendPost("/api/v1/correction-requests", employee,
                "{\"businessId\":"+business+",\"physicalCountId\":"+count+",\"reason\":\""+reason+"\"}",201));
    }
    org.springframework.test.web.servlet.ResultActions review(Long request, String token, String decision) throws Exception {
        return mvc.perform(patch("/api/v1/correction-requests/{id}/review",request).header("Authorization",token)
                .contentType("application/json").content("{\"businessId\":"+business+",\"decision\":\""+decision+"\",\"reviewComment\":\"Fixture\"}"));
    }
    int rows(String table) { return jdbc.queryForObject("select count(*) from "+table,Integer.class); }

    @Test void physicalCountUsesServerStockAndDoesNotApplyAnAdjustment() throws Exception {
        Long lot=lot(admin,business,product); movement(employee,business,lot,"ENTRY",10,201);
        Long count=count(lot,7);
        mvc.perform(get("/api/v1/physical-counts/{id}",count).header("Authorization",employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lotId").value(lot))
                .andExpect(jsonPath("$.systemQuantity").value(10)).andExpect(jsonPath("$.countedQuantity").value(7))
                .andExpect(jsonPath("$.difference").value(-3)).andExpect(jsonPath("$.countedAt").isString());
        mvc.perform(get("/api/v1/physical-counts").header("Authorization",employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        assertEquals(10,quantity(lot)); assertEquals(1,rows("inventory_movements"));
        assertEquals(0,rows("stock_adjustments")); assertEquals(2,rows("audit_logs"));
    }

    @Test void adminAdjustmentIsAtomicUniqueAndEmployeeCannotReadOrApplyIt() throws Exception {
        Long lot=lot(admin,business,product); movement(employee,business,lot,"ENTRY",10,201); Long count=count(lot,7);
        String body="{\"businessId\":"+business+",\"physicalCountId\":"+count+",\"reason\":\"Fixture\"}";
        sendPost("/api/v1/stock-adjustments",employee,body,403);
        mvc.perform(get("/api/v1/stock-adjustments").header("Authorization",employee)).andExpect(status().isForbidden());
        sendPost("/api/v1/stock-adjustments",admin,body,201);
        assertEquals(7,quantity(lot)); assertEquals(1,rows("stock_adjustments"));
        assertEquals(2,rows("inventory_movements")); assertEquals(4,rows("audit_logs"));
        sendPost("/api/v1/stock-adjustments",admin,body,409);
        assertEquals(7,quantity(lot)); assertEquals(1,rows("stock_adjustments")); assertEquals(4,rows("audit_logs"));
    }

    @Test void wasteOwnsOneExitAndRejectsInsufficientStockWithoutPartialWrites() throws Exception {
        Long lot=lot(admin,business,product); movement(employee,business,lot,"ENTRY",10,201);
        String body="{\"businessId\":"+business+",\"lotId\":"+lot+",\"quantity\":2,\"reason\":\"DAMAGED\",\"observation\":null}";
        sendPost("/api/v1/wastes",employee,body,201);
        assertEquals(8,quantity(lot)); assertEquals(1,rows("wastes")); assertEquals(2,rows("inventory_movements")); assertEquals(3,rows("audit_logs"));
        sendPost("/api/v1/wastes",employee,body.replace("\"quantity\":2","\"quantity\":20"),400);
        assertEquals(8,quantity(lot)); assertEquals(1,rows("wastes")); assertEquals(3,rows("audit_logs"));
        mvc.perform(get("/api/v1/wastes").header("Authorization",employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test void approvalOwnsItsAdjustmentAndRejectionLeavesStockUnchanged() throws Exception {
        Long lot=lot(admin,business,product); movement(employee,business,lot,"ENTRY",10,201);
        Long count=count(lot,7); Long request=request(count,"Fixture");
        review(request,employee,"APPROVED").andExpect(status().isForbidden());
        review(request,admin,"APPROVED").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        assertEquals(7,quantity(lot)); assertEquals(1,rows("stock_adjustments")); assertEquals(2,rows("inventory_movements"));
        assertEquals(6,rows("audit_logs"));
        Long second=request(count(lot,5),"Rejected fixture");
        review(second,admin,"REJECTED").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        assertEquals(7,quantity(lot)); assertEquals(1,rows("stock_adjustments")); assertEquals(2,rows("inventory_movements"));
    }

    @Test void staleCountIsRejectedWithRollback() throws Exception {
        Long lot=lot(admin,business,product); movement(employee,business,lot,"ENTRY",10,201);
        movement(employee,business,lot,"ENTRY",1,201);
        Long stale=request(count(lot,8),"á".repeat(500));
        movement(employee,business,lot,"ENTRY",1,201);
        int audits=rows("audit_logs"), movements=rows("inventory_movements");
        review(stale,admin,"APPROVED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Lot stock has changed since the physical count"));
        assertEquals(12,quantity(lot)); assertEquals(0,rows("stock_adjustments"));
        assertEquals(audits,rows("audit_logs")); assertEquals(movements,rows("inventory_movements"));
        assertEquals("PENDING",jdbc.queryForObject("select status from correction_requests where id=?",String.class,stale));
    }

    @Test void foreignReferencesAndMissingAuthenticationHaveNoEffects() throws Exception {
        Long foreignLot=lot(foreignAdmin,foreignBusiness,foreignProduct);
        sendPost("/api/v1/physical-counts",employee,"{\"businessId\":"+business+",\"lotId\":"+foreignLot+",\"countedQuantity\":1}",400);
        sendPost("/api/v1/wastes",employee,"{\"businessId\":"+business+",\"lotId\":"+foreignLot+",\"quantity\":1,\"reason\":\"DAMAGED\"}",400);
        Long own=lot(admin,business,product); Long ownCount=count(own,0);
        sendPost("/api/v1/correction-requests",foreignAdmin,"{\"businessId\":"+foreignBusiness+",\"physicalCountId\":"+ownCount+",\"reason\":\"Fixture\"}",404);
        mvc.perform(get("/api/v1/physical-counts/{id}",ownCount).header("Authorization",foreignAdmin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/correction-requests")).andExpect(status().isUnauthorized());
        assertEquals(0,rows("wastes")); assertEquals(0,rows("stock_adjustments")); assertEquals(0,rows("correction_requests")); assertEquals(0,rows("inventory_movements"));
    }

    Map<String,Object> snapshot(Long lot) {
        return Map.of("lot",jdbc.queryForMap("select * from lots where id=?",lot),
                "counts",jdbc.queryForList("select * from physical_counts order by id"),
                "requests",jdbc.queryForList("select * from correction_requests order by id"),
                "adjustments",jdbc.queryForList("select * from stock_adjustments order by id"),
                "movements",jdbc.queryForList("select * from inventory_movements order by id"),
                "audits",jdbc.queryForList("select * from audit_logs order by id"));
    }

    String adjustmentBody(Long count) {
        return "{\"businessId\":"+business+",\"physicalCountId\":"+count+",\"reason\":\"Fixture\"}";
    }

    String requestBody(Long count) {
        return "{\"businessId\":"+business+",\"physicalCountId\":"+count+",\"reason\":\"Fixture\"}";
    }

    @Test void pendingDuplicateReturns409AndLeavesEveryStockControlRecordUnchanged() throws Exception {
        Long lot=lot(admin,business,product); movement(employee,business,lot,"ENTRY",10,201);
        Long count=count(lot,7); request(count,"Fixture");
        var before=snapshot(lot);
        var result=sendPost("/api/v1/correction-requests",employee,requestBody(count),409);
        assertEquals(Map.of("message","A pending correction request already exists"),
                com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(),"$"));
        assertEquals(before,snapshot(lot));
    }

    @Test void countsWithoutDifferenceConflictForBothRequestsAndAdjustments() throws Exception {
        Long lot=lot(admin,business,product); Long count=count(lot,0);
        var before=snapshot(lot);
        sendPost("/api/v1/correction-requests",employee,requestBody(count),409);
        sendPost("/api/v1/stock-adjustments",admin,adjustmentBody(count),409);
        assertEquals(before,snapshot(lot));
    }

    @Test void adjustedCountCannotBeAdjustedOrRequestedAgain() throws Exception {
        Long lot=lot(admin,business,product); movement(employee,business,lot,"ENTRY",10,201);
        Long count=count(lot,7);
        sendPost("/api/v1/stock-adjustments",admin,adjustmentBody(count),201);
        var before=snapshot(lot);
        sendPost("/api/v1/stock-adjustments",admin,adjustmentBody(count),409);
        sendPost("/api/v1/correction-requests",employee,requestBody(count),409);
        assertEquals(before,snapshot(lot));
    }

    @Test void repeatedApprovalOrRejectionReturns409WithoutChangingReviewOrSideEffects() throws Exception {
        Long lot=lot(admin,business,product); movement(employee,business,lot,"ENTRY",10,201);
        for(String firstDecision:List.of("APPROVED","REJECTED")) {
            Long request=request(count(lot,quantity(lot)-1),"Fixture");
            review(request,admin,firstDecision).andExpect(status().isOk());
            var before=snapshot(lot);
            for(String repeatedDecision:List.of("APPROVED","REJECTED")) {
                review(request,admin,repeatedDecision).andExpect(status().isConflict())
                        .andExpect(jsonPath("$.message").value("Correction request has already been reviewed"));
                assertEquals(before,snapshot(lot));
            }
        }
    }

    @Test void staleDirectAdjustmentReturns409WithNoPartialMovementOrAudit() throws Exception {
        Long lot=lot(admin,business,product); movement(employee,business,lot,"ENTRY",10,201);
        Long count=count(lot,7); movement(employee,business,lot,"ENTRY",1,201);
        var before=snapshot(lot);
        var result=sendPost("/api/v1/stock-adjustments",admin,adjustmentBody(count),409);
        assertEquals(Map.of("message","Lot stock has changed since the physical count"),
                com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(),"$"));
        assertEquals(before,snapshot(lot));
    }

    @Test void invalidInputsStay400AndDoNotAlterStockControlState() throws Exception {
        Long lot=lot(admin,business,product); Long count=count(lot,0);
        var before=snapshot(lot);
        sendPost("/api/v1/correction-requests",employee,requestBody(count).replace("Fixture"," "),400);
        sendPost("/api/v1/stock-adjustments",admin,adjustmentBody(count).replace("Fixture","a".repeat(501)),400);
        sendPost("/api/v1/stock-adjustments",admin,"{",400);
        assertEquals(before,snapshot(lot));
        Long request=request(count(lot,1),"Fixture");
        var afterRequest=snapshot(lot);
        review(request,admin,"PENDING").andExpect(status().isBadRequest());
        review(request,admin,"UNKNOWN").andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/correction-requests").header("Authorization",admin)
                .param("businessId",business.toString()).param("physicalCountId",count.toString()))
                .andExpect(status().isBadRequest());
        assertEquals(afterRequest,snapshot(lot));
        assertEquals(before.get("movements"),afterRequest.get("movements"));
        assertEquals(before.get("adjustments"),afterRequest.get("adjustments"));
    }

    @Test void unexpectedAuditFailureReturnsSafe500AndRollsBackFlushedAdjustmentAndMovement() throws Exception {
        Long lot=lot(admin,business,product); movement(employee,business,lot,"ENTRY",10,201);
        Long request=request(count(lot,7),"á".repeat(499)+"Z");
        var before=snapshot(lot);
        // Deliberate fault only in this owned Testcontainer, after movement/adjustment writes.
        jdbc.execute("""
                CREATE FUNCTION a05_fail_adjustment_audit() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.action = 'STOCK_ADJUSTMENT_APPLIED' THEN
                        RAISE EXCEPTION 'A05_PRIVATE_INTERNAL_FIXTURE';
                    END IF;
                    RETURN NEW;
                END $$
                """);
        try {
            jdbc.execute("CREATE TRIGGER a05_audit_failure BEFORE INSERT ON audit_logs FOR EACH ROW EXECUTE FUNCTION a05_fail_adjustment_audit()");
            var result=review(request,admin,"APPROVED").andExpect(status().isInternalServerError()).andReturn();
            assertEquals(Map.of("message","Internal server error"),
                    com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(),"$"));
            assertEquals(before,snapshot(lot));
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS a05_audit_failure ON audit_logs");
            jdbc.execute("DROP FUNCTION a05_fail_adjustment_audit()");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {255, 256, 500})
    void approvalPreservesCompleteReasonInRequestAdjustmentAndMovement(int length) throws Exception {
        String reason="á".repeat(length-1)+"Z";
        Long lot=lot(admin,business,product); movement(employee,business,lot,"ENTRY",10,201);
        Long count=count(lot,7); Long request=request(count,reason);
        mvc.perform(get("/api/v1/correction-requests/{id}",request).header("Authorization",employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reason").value(reason));
        mvc.perform(get("/api/v1/correction-requests/{id}",request).header("Authorization",foreignAdmin))
                .andExpect(status().isNotFound());
        var pending=snapshot(lot);
        review(request,employee,"APPROVED").andExpect(status().isForbidden());
        assertEquals(pending,snapshot(lot));
        review(request,admin,"APPROVED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reason").value(reason));
        assertEquals(7,quantity(lot)); assertEquals(1,rows("stock_adjustments"));
        assertEquals(2,rows("inventory_movements")); assertEquals(6,rows("audit_logs"));
        assertEquals(reason,jdbc.queryForObject("select reason from correction_requests where id=?",String.class,request));
        assertEquals(reason,jdbc.queryForObject("select reason from stock_adjustments where physical_count_id=?",String.class,count));
        Long movementId=jdbc.queryForObject("select id from inventory_movements where lot_id=? and type='EXIT'",Long.class,lot);
        mvc.perform(get("/api/v1/inventory-movements/{id}",movementId).header("Authorization",employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reason").value(reason));
        mvc.perform(get("/api/v1/stock-adjustments").param("physicalCountId",count.toString()).header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].reason").value(reason));
        var approved=snapshot(lot);
        review(request,admin,"APPROVED").andExpect(status().isConflict());
        assertEquals(approved,snapshot(lot));
    }

    @ParameterizedTest
    @ValueSource(ints = {255, 256, 500})
    void rejectionPreservesCompleteReasonWithoutChangingStock(int length) throws Exception {
        String reason="á".repeat(length-1)+"Z";
        Long lot=lot(admin,business,product); movement(employee,business,lot,"ENTRY",10,201);
        Long request=request(count(lot,7),reason);
        review(request,admin,"REJECTED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value(reason));
        assertEquals(reason,jdbc.queryForObject("select reason from correction_requests where id=?",String.class,request));
        assertEquals(10,quantity(lot)); assertEquals(0,rows("stock_adjustments"));
        assertEquals(1,rows("inventory_movements")); assertEquals(4,rows("audit_logs"));
    }

    @ParameterizedTest
    @ValueSource(ints = {255, 256, 500})
    void directAdjustmentsAcceptTheSameCompleteReasonWithoutDuplicatingEffects(int length) throws Exception {
        String reason="á".repeat(length-1)+"Z";
        Long lot=lot(admin,business,product); movement(employee,business,lot,"ENTRY",10,201);
        Long count=count(lot,7);
        var result=sendPost("/api/v1/stock-adjustments",admin,adjustmentBody(count).replace("Fixture",reason),201);
        assertEquals(reason,com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(),"$.reason"));
        assertEquals(reason,jdbc.queryForObject("select reason from inventory_movements where lot_id=? and type='EXIT'",String.class,lot));
        assertEquals(7,quantity(lot)); assertEquals(1,rows("stock_adjustments"));
        assertEquals(2,rows("inventory_movements")); assertEquals(4,rows("audit_logs"));
    }

    @Test void overlongReasonsAre400AndManualMovementInputKeepsItsExisting255Limit() throws Exception {
        Long lot=lot(admin,business,product); movement(employee,business,lot,"ENTRY",10,201);
        Long count=count(lot,7); var before=snapshot(lot);
        sendPost("/api/v1/correction-requests",employee,requestBody(count).replace("Fixture","a".repeat(501)),400);
        sendPost("/api/v1/stock-adjustments",admin,adjustmentBody(count).replace("Fixture","a".repeat(501)),400);
        sendPost("/api/v1/inventory-movements",employee,movementBody(business,lot,"ENTRY",1).replace("Fixture","a".repeat(256)),400);
        assertEquals(before,snapshot(lot));
    }
}
