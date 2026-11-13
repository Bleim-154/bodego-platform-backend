package com.bodego.platform.shared.infrastructure.security;

import com.bodego.platform.catalog.domain.model.aggregates.*;
import com.bodego.platform.catalog.infrastructure.persistence.jpa.repositories.*;
import com.bodego.platform.catalog.domain.services.*;
import com.bodego.platform.catalog.domain.model.commands.*;
import com.bodego.platform.catalog.domain.model.queries.*;
import com.bodego.platform.inventory.domain.model.aggregates.Lot;
import com.bodego.platform.inventory.infrastructure.persistence.jpa.repositories.LotRepository;
import com.bodego.platform.operations.domain.model.aggregates.*;
import com.bodego.platform.operations.domain.exceptions.PhysicalCountStockChangedException;
import com.bodego.platform.operations.domain.exceptions.PendingCorrectionRequestAlreadyExistsException;
import com.bodego.platform.operations.domain.model.commands.*;
import com.bodego.platform.operations.domain.model.valueobjects.*;
import com.bodego.platform.operations.domain.services.*;
import com.bodego.platform.operations.infrastructure.persistence.jpa.repositories.*;
import com.bodego.platform.monitoring.domain.model.aggregates.*;
import com.bodego.platform.monitoring.domain.model.commands.RecordAuditLogCommand;
import com.bodego.platform.monitoring.domain.model.valueobjects.*;
import com.bodego.platform.monitoring.domain.services.AuditLogCommandService;
import com.bodego.platform.monitoring.infrastructure.persistence.jpa.repositories.*;
import com.bodego.platform.strategicoffers.domain.model.aggregates.*;
import com.bodego.platform.strategicoffers.domain.model.valueobjects.RecommendationType;
import com.bodego.platform.strategicoffers.infrastructure.persistence.jpa.repositories.*;
import com.bodego.platform.iam.domain.model.aggregates.*;
import com.bodego.platform.iam.domain.model.valueobjects.*;
import com.bodego.platform.iam.domain.services.EmployeeAdministrationCommandService;
import com.bodego.platform.iam.domain.model.commands.ChangeEmployeeStatusCommand;
import com.bodego.platform.iam.infrastructure.hashing.BCryptPasswordHashingService;
import com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.*;
import com.bodego.platform.iam.application.internal.outboundservices.tokens.AccessTokenService;
import com.bodego.platform.shared.application.security.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class BusinessContextSecurityTests {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("bodego_security_multibusiness_isolated").withStartupTimeout(Duration.ofMinutes(2));
    static final String KEY=Base64.getEncoder().encodeToString(java.security.SecureRandom.getSeed(32));
    static final PasswordHash HASH=new BCryptPasswordHashingService().hash("Fixture-password-2026");
    @DynamicPropertySource static void isolated(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",POSTGRES::getUsername);
        registry.add("spring.datasource.password",POSTGRES::getPassword);
        registry.add("spring.flyway.enabled",() -> true);
        registry.add("bodego.security.jwt.secret-base64",() -> KEY);
        registry.add("bodego.security.cors.allowed-origins",() -> "http://localhost:4200");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccessTokenService tokens;
    @Autowired BusinessRepository businesses;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired LotRepository lots;
    @Autowired PhysicalCountRepository counts;
    @Autowired CorrectionRequestRepository requests;
    @Autowired StockAdjustmentRepository adjustments;
    @Autowired InventoryMovementRepository movements;
    @Autowired WasteRepository wastes;
    @Autowired AlertRepository alerts;
    @Autowired AuditLogRepository audits;
    @Autowired RecommendationRepository recommendations;
    @Autowired OfferRepository offers;
    @Autowired CategoryCommandService categoryCommands;
    @Autowired CategoryQueryService categoryQueries;
    @Autowired InventoryMovementCommandService movementCommands;
    @Autowired CorrectionRequestCommandService correctionCommands;
    @Autowired AuditLogCommandService auditCommands;
    @Autowired EmployeeAdministrationCommandService employeeCommands;
    @Autowired PlatformTransactionManager transactions;
    @Autowired RequestMappingHandlerMapping mappings;
    Long business,foreignBusiness,adminId,employeeId,foreignAdminId;
    Category category,foreignCategory;
    Product product,foreignProduct;
    Lot lot,foreignLot;
    PhysicalCount count,foreignCount;
    CorrectionRequest correction,foreignCorrection;
    String admin,employee;
    final Map<String,Long> ownIds=new LinkedHashMap<>(),foreignIds=new LinkedHashMap<>();

    @BeforeEach void fixtures() {
        assertEquals("bodego_security_multibusiness_isolated",jdbc.queryForObject("select current_database()",String.class));
        assertNotEquals(5432,POSTGRES.getMappedPort(5432));
        for(String table:List.of("audit_logs","alerts","recommendations","offers","stock_adjustments","correction_requests",
                "physical_counts","wastes","inventory_movements","lots","products","categories","users","businesses")) jdbc.update("delete from "+table);
        business=businesses.saveAndFlush(new Business("Fixture A","Fixture address","123")).getId();
        foreignBusiness=businesses.saveAndFlush(new Business("Fixture B","Fixture address","456")).getId();
        adminId=users.saveAndFlush(new User(business,"Fixture admin",new EmailAddress("admin-a@example.invalid"),HASH,UserRole.ADMIN)).getId();
        employeeId=users.saveAndFlush(new User(business,"Fixture employee",new EmailAddress("employee-a@example.invalid"),HASH,UserRole.EMPLOYEE)).getId();
        foreignAdminId=users.saveAndFlush(new User(foreignBusiness,"Foreign fixture",new EmailAddress("admin-b@example.invalid"),HASH,UserRole.ADMIN)).getId();
        category=categories.saveAndFlush(new Category(business,"Own fixture",null));
        foreignCategory=categories.saveAndFlush(new Category(foreignBusiness,"Foreign fixture",null));
        product=products.saveAndFlush(new Product(business,category.getId(),"Own product",null,BigDecimal.TEN,20));
        foreignProduct=products.saveAndFlush(new Product(foreignBusiness,foreignCategory.getId(),"Foreign product",null,BigDecimal.TEN,20));
        lot=lots.saveAndFlush(new Lot(business,product.getId(),"Own batch",10,LocalDate.now(ZoneOffset.UTC).plusDays(2)));
        foreignLot=lots.saveAndFlush(new Lot(foreignBusiness,foreignProduct.getId(),"Foreign batch",10,LocalDate.now(ZoneOffset.UTC).plusDays(2)));
        count=counts.saveAndFlush(new PhysicalCount(business,lot.getId(),10,8,"Own fixture"));
        foreignCount=counts.saveAndFlush(new PhysicalCount(foreignBusiness,foreignLot.getId(),10,8,"Foreign fixture"));
        correction=requests.saveAndFlush(new CorrectionRequest(business,count.getId(),"Own fixture"));
        foreignCorrection=requests.saveAndFlush(new CorrectionRequest(foreignBusiness,foreignCount.getId(),"Foreign fixture"));
        seedDirectory(business,adminId,category,product,lot,count,correction,ownIds);
        seedDirectory(foreignBusiness,foreignAdminId,foreignCategory,foreignProduct,foreignLot,foreignCount,foreignCorrection,foreignIds);
        admin=bearer(adminId,business,"ADMIN");employee=bearer(employeeId,business,"EMPLOYEE");
    }
    void seedDirectory(Long tenant,Long actor,Category category,Product product,Lot lot,PhysicalCount count,CorrectionRequest correction,Map<String,Long> ids) {
        ids.clear();ids.put("categories",category.getId());ids.put("products",product.getId());ids.put("lots",lot.getId());
        ids.put("physical-counts",count.getId());ids.put("correction-requests",correction.getId());
        ids.put("inventory-movements",movements.saveAndFlush(new InventoryMovement(tenant,lot.getId(),MovementType.ENTRY,1,"Fixture")).getId());
        ids.put("wastes",wastes.saveAndFlush(new Waste(tenant,lot.getId(),1,WasteReason.DAMAGED,"Fixture")).getId());
        var adjusted=counts.saveAndFlush(new PhysicalCount(tenant,lot.getId(),10,9,"Adjustment fixture"));
        ids.put("stock-adjustments",adjustments.saveAndFlush(new StockAdjustment(tenant,adjusted.getId(),lot.getId(),10,9,"Fixture")).getId());
        ids.put("alerts",alerts.saveAndFlush(new Alert(tenant,product.getId(),lot.getId(),AlertType.EXPIRING_SOON,AlertSeverity.MEDIUM,"Fixture")).getId());
        ids.put("audit-logs",audits.saveAndFlush(new AuditLog(tenant,actor,"FIXTURE","Product",product.getId(),"Fixture")).getId());
        ids.put("recommendations",recommendations.saveAndFlush(new Recommendation(tenant,product.getId(),null,RecommendationType.RESTOCK,"Fixture",Instant.now())).getId());
        ids.put("offers",offers.saveAndFlush(new Offer(tenant,product.getId(),"Fixture",BigDecimal.TEN,LocalDate.now(),LocalDate.now().plusDays(3))).getId());
    }
    @AfterEach void clear() {SecurityContextHolder.clearContext();}
    String bearer(Long user,Long tenant,String role) {return "Bearer "+tokens.issue(new AuthenticatedIdentity(user,tenant,role)).accessToken();}
    void runAs(Long user,Long tenant,String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedIdentity(user,tenant,role),null,List.of()));
    }
    ResultActions send(String method,String path,String bearer,String json) throws Exception {
        var builder=request(HttpMethod.valueOf(method),path);
        if(bearer!=null)builder.header("Authorization",bearer);
        if(json!=null)builder.contentType("application/json").content(json);
        return mvc.perform(builder);
    }
    Long id(MvcResult result) throws Exception {return ((Number)com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(),"$.id")).longValue();}
    void matrix(String token,String role) throws Exception {
        for(var route:BusinessEndpointMatrix.ROUTES) {
            String url=route.path().replace("{id}",Long.toString(Long.MAX_VALUE));
            boolean permitted=role.equals("ADMIN")?route.admin():route.employee();
            int expected=token==null?401:!permitted?403:route.method().equals("GET")?(route.path().contains("{id}")?404:200)
                    :route.method().equals("PATCH")&&!route.path().endsWith("/review")?404:400;
            String body=Set.of("POST","PUT").contains(route.method()) || route.path().endsWith("/review")?"{":null;
            var result=send(route.method(),url,token,body).andReturn();
            assertEquals(expected,result.getResponse().getStatus(),role+" "+route.method()+" "+route.path()+" => "+result.getResponse().getContentAsString());
        }
    }
    @Test void matrixAdminCoversEveryExistingBusinessRoute() throws Exception {
        Set<String> actual=new HashSet<>();
        mappings.getHandlerMethods().forEach((info,handler)-> {
            if(handler.getBeanType().getName().matches("com\\.bodego\\.platform\\.(catalog|inventory|operations|monitoring|strategicoffers)\\.interfaces\\.rest\\..*"))
                for(var method:info.getMethodsCondition().getMethods())for(String pattern:info.getPatternValues())actual.add(method.name()+" "+pattern);
        });
        assertEquals(new HashSet<>(BusinessEndpointMatrix.ROUTES.stream().map(r->r.method()+" "+r.path()).toList()),actual);
        matrix(admin,"ADMIN");
    }
    @Test void matrixEmployeeAppliesExplicitPermissionsOnly() throws Exception {matrix(employee,"EMPLOYEE");}
    @Test void matrixAnonymousRejectsEveryBusinessRoute() throws Exception {matrix(null,"ANONYMOUS");}
    @Test void unfilteredListsAndEveryResourceIdAreTenantScoped() throws Exception {
        for(var entry:ownIds.entrySet()) {
            String path="/api/v1/"+entry.getKey();
            var body=send("GET",path,admin,null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Map<String,Object>> nodes=com.jayway.jsonpath.JsonPath.read(body,"$");
            assertFalse(nodes.isEmpty(),path);
            for(var node:nodes)assertEquals(business.longValue(),((Number)node.get("businessId")).longValue(),path);
            send("GET",path+"/"+entry.getValue(),admin,null).andExpect(status().isOk());
            String foreign=send("GET",path+"/"+foreignIds.get(entry.getKey()),admin,null)
                    .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
            String absent=send("GET",path+"/"+Long.MAX_VALUE,admin,null)
                    .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
            assertEquals(absent,foreign,path);
            send("GET",path+"?businessId="+foreignBusiness,admin,null).andExpect(status().isForbidden());
        }
    }
    @Test void relationFiltersCannotEnumerateForeignEntities() throws Exception {
        for(String url:List.of("products?categoryId="+foreignCategory.getId(),"lots?productId="+foreignProduct.getId(),
                "inventory-movements?lotId="+foreignLot.getId(),"physical-counts?lotId="+foreignLot.getId(),
                "wastes?lotId="+foreignLot.getId(),"correction-requests?physicalCountId="+foreignCount.getId(),
                "stock-adjustments?physicalCountId="+foreignCount.getId(),"alerts?productId="+foreignProduct.getId(),
                "offers?productId="+foreignProduct.getId(),"recommendations?productId="+foreignProduct.getId()))
            send("GET","/api/v1/"+url,admin,null).andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @Test void employeeMovementAndWasteUpdateOnlyOwnStockAndAuditActualActor() throws Exception {
        long before=audits.count();
        send("POST","/api/v1/inventory-movements",employee,"{\"businessId\":"+business+",\"lotId\":"+lot.getId()+",\"type\":\"ENTRY\",\"quantity\":3,\"reason\":\"Fixture\"}")
                .andExpect(status().isCreated());
        send("POST","/api/v1/wastes",employee,"{\"businessId\":"+business+",\"lotId\":"+lot.getId()+",\"quantity\":2,\"reason\":\"DAMAGED\"}")
                .andExpect(status().isCreated());
        assertEquals(11,lots.findById(lot.getId()).orElseThrow().getQuantity());
        assertEquals(10,lots.findById(foreignLot.getId()).orElseThrow().getQuantity());
        assertEquals(before+3,audits.count());
        assertEquals(3,jdbc.queryForObject("select count(*) from audit_logs where business_id=? and user_id=?",Integer.class,business,employeeId));
    }
    @Test void adminCatalogAndLotEditsPreserveAuthenticatedBusinessAndRelationships() throws Exception {
        Long newCategory=id(send("POST","/api/v1/categories",admin,"{\"businessId\":"+business+",\"name\":\"New fixture\"}")
                .andExpect(status().isCreated()).andReturn());
        Long newProduct=id(send("POST","/api/v1/products",admin,"{\"businessId\":"+business+",\"categoryId\":"+newCategory+",\"name\":\"New product\",\"price\":12,\"minimumStock\":3}")
                .andExpect(status().isCreated()).andReturn());
        send("PUT","/api/v1/products/"+newProduct,admin,"{\"categoryId\":"+newCategory+",\"name\":\"Updated product\",\"price\":13,\"minimumStock\":2}")
                .andExpect(status().isOk());
        send("PATCH","/api/v1/products/"+newProduct+"/deactivate",admin,null).andExpect(status().isOk());
        send("PATCH","/api/v1/products/"+newProduct+"/activate",admin,null).andExpect(status().isOk());
        Long newLot=id(send("POST","/api/v1/lots",admin,"{\"businessId\":"+business+",\"productId\":"+newProduct+",\"batchNumber\":\"New fixture\",\"quantity\":0,\"expirationDate\":\"2027-12-31\"}")
                .andExpect(status().isCreated()).andReturn());
        send("PUT","/api/v1/lots/"+newLot,admin,"{\"batchNumber\":\"Changed fixture\",\"expirationDate\":\"2028-12-31\"}")
                .andExpect(status().isOk());
        assertEquals(business,products.findById(newProduct).orElseThrow().getBusinessId());
        assertEquals(business,lots.findById(newLot).orElseThrow().getBusinessId());
        assertEquals(0,lots.findById(newLot).orElseThrow().getQuantity());
    }
    Map<String,Long> rowCounts() {
        var result=new LinkedHashMap<String,Long>();
        for(String table:List.of("categories","products","lots","inventory_movements","physical_counts","wastes",
                "correction_requests","stock_adjustments","audit_logs","alerts","offers","recommendations"))
            result.put(table,jdbc.queryForObject("select count(*) from "+table,Long.class));
        return result;
    }
    @Test void forgedTenantAndForeignReferenceCommandsCannotWriteAcrossContexts() throws Exception {
        var before=rowCounts();
        send("POST","/api/v1/categories",admin,"{\"businessId\":"+foreignBusiness+",\"name\":\"Denied\"}").andExpect(status().isForbidden());
        send("POST","/api/v1/inventory-movements",employee,"{\"businessId\":"+foreignBusiness+",\"lotId\":"+foreignLot.getId()+",\"type\":\"ENTRY\",\"quantity\":1,\"reason\":\"Denied\"}")
                .andExpect(status().isForbidden());
        record Attempt(String path,String template,Long foreignId) {}
        var attempts=List.of(
                new Attempt("products","{\"businessId\":"+business+",\"categoryId\":%d,\"name\":\"Denied\",\"price\":1,\"minimumStock\":0}",foreignCategory.getId()),
                new Attempt("lots","{\"businessId\":"+business+",\"productId\":%d,\"batchNumber\":\"Denied\",\"quantity\":0}",foreignProduct.getId()),
                new Attempt("inventory-movements","{\"businessId\":"+business+",\"lotId\":%d,\"type\":\"ENTRY\",\"quantity\":1,\"reason\":\"Denied\"}",foreignLot.getId()),
                new Attempt("physical-counts","{\"businessId\":"+business+",\"lotId\":%d,\"countedQuantity\":5}",foreignLot.getId()),
                new Attempt("wastes","{\"businessId\":"+business+",\"lotId\":%d,\"quantity\":1,\"reason\":\"DAMAGED\"}",foreignLot.getId()),
                new Attempt("correction-requests","{\"businessId\":"+business+",\"physicalCountId\":%d,\"reason\":\"Denied\"}",foreignCount.getId()),
                new Attempt("stock-adjustments","{\"businessId\":"+business+",\"physicalCountId\":%d,\"reason\":\"Denied\"}",foreignCount.getId()),
                new Attempt("offers","{\"businessId\":"+business+",\"productId\":%d,\"title\":\"Denied\",\"discountPercentage\":10,\"startDate\":\"2026-11-12\",\"endDate\":\"2027-11-12\"}",foreignProduct.getId()));
        for(var attempt:attempts) {
            var foreign=send("POST","/api/v1/"+attempt.path(),admin,attempt.template().formatted(attempt.foreignId())).andReturn().getResponse();
            var absent=send("POST","/api/v1/"+attempt.path(),admin,attempt.template().formatted(Long.MAX_VALUE)).andReturn().getResponse();
            assertTrue(Set.of(400,404).contains(foreign.getStatus()),attempt.path());
            assertEquals(absent.getStatus(),foreign.getStatus(),attempt.path());
            assertEquals(absent.getContentAsString(),foreign.getContentAsString(),attempt.path());
        }
        assertEquals(before,rowCounts());
        assertEquals(10,lots.findById(lot.getId()).orElseThrow().getQuantity());
        assertEquals(10,lots.findById(foreignLot.getId()).orElseThrow().getQuantity());
    }
    @Test void foreignAndAbsentWriteTargetsAreEquivalentAndLeaveNoPartialWrites() throws Exception {
        var before=rowCounts();
        record Attempt(String method,String path,String body,String entity) {}
        var attempts=List.of(
                new Attempt("PUT","categories/%d","{\"name\":\"Denied\"}","categories"),
                new Attempt("PUT","products/%d","{\"categoryId\":"+category.getId()+",\"name\":\"Denied\",\"price\":1,\"minimumStock\":0}","products"),
                new Attempt("PUT","lots/%d","{\"batchNumber\":\"Denied\"}","lots"),
                new Attempt("PATCH","alerts/%d/resolve",null,"alerts"),
                new Attempt("PATCH","recommendations/%d/dismiss",null,"recommendations"),
                new Attempt("PATCH","offers/%d/activate",null,"offers"),
                new Attempt("PATCH","offers/%d/deactivate",null,"offers"),
                new Attempt("PUT","offers/%d","{\"title\":\"Denied\",\"discountPercentage\":10,\"startDate\":\"2026-11-12\",\"endDate\":\"2027-11-12\"}","offers"),
                new Attempt("PATCH","correction-requests/%d/review","{\"businessId\":"+business+",\"decision\":\"APPROVED\"}","correction-requests"));
        for(var attempt:attempts) {
            var foreign=send(attempt.method(),"/api/v1/"+attempt.path().formatted(foreignIds.get(attempt.entity())),admin,attempt.body())
                    .andExpect(status().isNotFound()).andReturn().getResponse();
            var absent=send(attempt.method(),"/api/v1/"+attempt.path().formatted(Long.MAX_VALUE),admin,attempt.body())
                    .andExpect(status().isNotFound()).andReturn().getResponse();
            assertEquals(absent.getContentAsString(),foreign.getContentAsString(),attempt.entity());
        }
        assertEquals(before,rowCounts());
        assertEquals(CorrectionRequestStatus.PENDING,requests.findById(foreignCorrection.getId()).orElseThrow().getStatus());
    }
    @Test void adminMonitoringAndStrategicWritesUseOwnRelationsAndActor() throws Exception {
        for(String path:List.of("alerts/generate","recommendations/generate")) {
            String body=send("POST","/api/v1/"+path,admin,"{\"businessId\":"+business+"}")
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Map<String,Object>> generated=com.jayway.jsonpath.JsonPath.read(body,"$");
            for(var item:generated)
                assertEquals(business.longValue(),((Number)item.get("businessId")).longValue());
            send("POST","/api/v1/"+path,admin,"{\"businessId\":"+foreignBusiness+"}").andExpect(status().isForbidden());
        }
        send("PATCH","/api/v1/alerts/"+ownIds.get("alerts")+"/resolve",admin,null).andExpect(status().isOk());
        Long created=id(send("POST","/api/v1/offers",admin,"{\"businessId\":"+business+",\"productId\":"+product.getId()+",\"title\":\"Admin fixture\",\"discountPercentage\":15,\"startDate\":\"2026-11-12\",\"endDate\":\"2027-11-12\"}")
                .andExpect(status().isCreated()).andReturn());
        send("PATCH","/api/v1/offers/"+created+"/deactivate",admin,null).andExpect(status().isOk());
        assertEquals(0,jdbc.queryForObject("select count(*) from audit_logs where action<>'FIXTURE' and (business_id<>? or user_id<>? or user_id is null)",Integer.class,business,adminId));
    }
    @Test void stalePhysicalCountApprovalDoesNotCommitAnyPartOfStockOrAudit() {
        jdbc.update("update lots set quantity=11 where id=?",lot.getId());
        var before=rowCounts();runAs(adminId,business,"ADMIN");
        assertThrows(PhysicalCountStockChangedException.class,()->correctionCommands.handle(
                new ReviewCorrectionRequestCommand(business,correction.getId(),CorrectionRequestStatus.APPROVED,null)));
        assertEquals(before,rowCounts());
        assertEquals(11,lots.findById(lot.getId()).orElseThrow().getQuantity());
        assertEquals(CorrectionRequestStatus.PENDING,requests.findById(correction.getId()).orElseThrow().getStatus());
    }
    @Test void concurrentPendingRequestsForSameCountCreateExactlyOneRequest() throws Exception {
        var fresh=counts.saveAndFlush(new PhysicalCount(business,lot.getId(),10,6,"Concurrent fixture"));
        var barrier=new CyclicBarrier(2);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Object> action=()->{
                runAs(employeeId,business,"EMPLOYEE");
                try {barrier.await(10,TimeUnit.SECONDS);
                    try {return correctionCommands.handle(new CreateCorrectionRequestCommand(business,fresh.getId(),"Concurrent fixture"));}
                    catch(PendingCorrectionRequestAlreadyExistsException expected){return expected;}
                } finally {SecurityContextHolder.clearContext();}
            };
            var a=executor.submit(action);var b=executor.submit(action);
            var results=List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
            assertEquals(1,results.stream().filter(Long.class::isInstance).count());
            assertEquals(1,results.stream().filter(PendingCorrectionRequestAlreadyExistsException.class::isInstance).count());
            assertEquals(1,jdbc.queryForObject("select count(*) from correction_requests where physical_count_id=?",Integer.class,fresh.getId()));
            assertEquals(1,jdbc.queryForObject("select count(*) from audit_logs where entity_type='CorrectionRequest' and action='CORRECTION_REQUEST_CREATED'",Integer.class));
        }
    }
    @Test void employeeCountAndRequestThenAdminApprovalAreAtomicAndCorrectlyAttributed() throws Exception {
        var created=send("POST","/api/v1/physical-counts",employee,"{\"businessId\":"+business+",\"lotId\":"+lot.getId()+",\"countedQuantity\":7}")
                .andExpect(status().isCreated()).andReturn();
        Long countId=id(created);
        assertEquals(10,lots.findById(lot.getId()).orElseThrow().getQuantity());
        var requested=send("POST","/api/v1/correction-requests",employee,"{\"businessId\":"+business+",\"physicalCountId\":"+countId+",\"reason\":\"Fixture\"}")
                .andExpect(status().isCreated()).andReturn();
        String review="{\"businessId\":"+business+",\"decision\":\"APPROVED\"}";
        send("PATCH","/api/v1/correction-requests/"+id(requested)+"/review",employee,review).andExpect(status().isForbidden());
        send("PATCH","/api/v1/correction-requests/"+id(requested)+"/review",admin,review).andExpect(status().isOk());
        assertEquals(7,lots.findById(lot.getId()).orElseThrow().getQuantity());
        assertEquals(1,jdbc.queryForObject("select count(*) from stock_adjustments where physical_count_id=? and business_id=?",Integer.class,countId,business));
        assertEquals(2,jdbc.queryForObject("select count(*) from audit_logs where user_id=? and business_id=?",Integer.class,employeeId,business));
        assertEquals(3,jdbc.queryForObject("select count(*) from audit_logs where user_id=? and business_id=? and action<>'FIXTURE'",Integer.class,adminId,business));
    }
    @Test void directApplicationCallsCannotBypassRoleTenantOrAuditActor() {
        runAs(employeeId,business,"EMPLOYEE");
        assertThrows(BusinessAccessDeniedException.class,()->categoryCommands.handle(new CreateCategoryCommand(business,"Denied",null)));
        assertThrows(BusinessAccessDeniedException.class,()->movementCommands.handle(new CreateInventoryMovementCommand(foreignBusiness,foreignLot.getId(),MovementType.ENTRY,1,"Denied")));
        runAs(adminId,business,"ADMIN");
        assertThrows(BusinessAccessDeniedException.class,()->categoryCommands.handle(new DeleteCategoryCommand(category.getId())));
        var tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(status->auditCommands.handle(new RecordAuditLogCommand(business,foreignAdminId,"ACTOR_TEST","Product",product.getId(),"Fixture")));
        assertEquals(adminId,jdbc.queryForObject("select user_id from audit_logs where action='ACTOR_TEST'",Long.class));
        runAs(adminId,foreignBusiness,"ADMIN");
        assertThrows(BusinessAccessDeniedException.class,()->categoryQueries.handle(new GetAllCategoriesQuery()));
        SecurityContextHolder.clearContext();
        assertThrows(org.springframework.security.core.AuthenticationException.class,()->categoryQueries.handle(new GetAllCategoriesQuery()));
    }
    @Test void identityChangeInsideCallerTransactionRollsBackAllPriorWrites() {
        long movementCount=movements.count(),auditCount=audits.count();
        runAs(employeeId,business,"EMPLOYEE");
        assertThrows(BusinessAccessDeniedException.class,()->new TransactionTemplate(transactions).executeWithoutResult(status->{
            movementCommands.handle(new CreateInventoryMovementCommand(business,lot.getId(),MovementType.ENTRY,5,"Fixture rollback"));
            runAs(foreignAdminId,foreignBusiness,"ADMIN");
            auditCommands.handle(new RecordAuditLogCommand(business,null,"DENIED","Lot",lot.getId(),"Fixture"));
        }));
        assertEquals(10,lots.findById(lot.getId()).orElseThrow().getQuantity());
        assertEquals(movementCount,movements.count());assertEquals(auditCount,audits.count());
    }
    @Test void actorDeactivationWaitsForAuthorizedWriteThenBlocksNextRequest() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var disabling=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var writing=executor.submit(()->{
                runAs(employeeId,business,"EMPLOYEE");
                try {new TransactionTemplate(transactions).executeWithoutResult(status->{
                    movementCommands.handle(new CreateInventoryMovementCommand(business,lot.getId(),MovementType.ENTRY,1,"Concurrent fixture"));
                    entered.countDown();
                    try {assertTrue(release.await(15,TimeUnit.SECONDS));}catch(InterruptedException ex){throw new RuntimeException(ex);}
                });}finally{SecurityContextHolder.clearContext();}
            });
            assertTrue(entered.await(15,TimeUnit.SECONDS));
            var deactivation=executor.submit(()->{
                runAs(adminId,business,"ADMIN");disabling.countDown();
                try {employeeCommands.handle(new ChangeEmployeeStatusCommand(employeeId,false));}
                finally{SecurityContextHolder.clearContext();}
            });
            assertTrue(disabling.await(5,TimeUnit.SECONDS));
            try {assertThrows(TimeoutException.class,()->deactivation.get(200,TimeUnit.MILLISECONDS));}
            finally {release.countDown();}
            writing.get(15,TimeUnit.SECONDS);deactivation.get(15,TimeUnit.SECONDS);
        } finally {release.countDown();}
        assertEquals(11,lots.findById(lot.getId()).orElseThrow().getQuantity());
        assertFalse(users.findById(employeeId).orElseThrow().isEnabled());
        send("GET","/api/v1/lots",employee,null).andExpect(status().isUnauthorized());
    }
}
