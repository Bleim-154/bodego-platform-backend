package com.bodego.platform.iam.infrastructure;

import com.bodego.platform.iam.application.internal.outboundservices.tokens.AccessTokenService;
import com.bodego.platform.iam.domain.exceptions.*;
import com.bodego.platform.iam.domain.model.aggregates.*;
import com.bodego.platform.iam.domain.model.commands.*;
import com.bodego.platform.iam.domain.model.queries.*;
import com.bodego.platform.iam.domain.model.valueobjects.*;
import com.bodego.platform.iam.domain.services.*;
import com.bodego.platform.iam.infrastructure.hashing.BCryptPasswordHashingService;
import com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.*;
import com.bodego.platform.shared.application.security.AuthenticatedIdentity;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
@Testcontainers
class EmployeeManagementHttpTests {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("bodego_employee_management_isolated");
    static final PasswordHash HASH=new BCryptPasswordHashingService().hash("Fixture-password-2026");
    static final String KEY=Base64.getEncoder().encodeToString(java.security.SecureRandom.getSeed(32));
    @DynamicPropertySource static void isolated(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",POSTGRES::getUsername);
        registry.add("spring.datasource.password",POSTGRES::getPassword);
        registry.add("spring.flyway.enabled",() -> true);
        registry.add("bodego.security.jwt.secret-base64",() -> KEY);
        registry.add("bodego.security.cors.allowed-origins",() -> "http://localhost:4200");
    }
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired BusinessRepository businesses;
    @Autowired AccessTokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmployeeAdministrationCommandService commands;
    @Autowired EmployeeAdministrationQueryService queries;
    Long businessId,otherBusinessId,adminId,employeeId,otherEmployeeId;
    String admin,employee;
    @BeforeEach void fixtures() {
        assertNotEquals("bodego_db",jdbc.queryForObject("select current_database()",String.class));
        jdbc.update("delete from users");jdbc.update("delete from businesses");
        businessId=businesses.saveAndFlush(new Business("Fixture","Fixture address","123")).getId();
        otherBusinessId=businesses.saveAndFlush(new Business("Other fixture","Other address","456")).getId();
        adminId=saveUser(businessId,"admin@example.invalid",UserRole.ADMIN);
        employeeId=saveUser(businessId,"employee@example.invalid",UserRole.EMPLOYEE);
        otherEmployeeId=saveUser(otherBusinessId,"other@example.invalid",UserRole.EMPLOYEE);
        admin="Bearer "+tokens.issue(new AuthenticatedIdentity(adminId,businessId,"ADMIN")).accessToken();
        employee="Bearer "+tokens.issue(new AuthenticatedIdentity(employeeId,businessId,"EMPLOYEE")).accessToken();
    }
    Long saveUser(Long business,String email,UserRole role) {
        return users.saveAndFlush(new User(business,"Fixture user",new EmailAddress(email),HASH,role)).getId();
    }
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }
    String registration(String email) {
        return "{\"fullName\":\"New fixture\",\"email\":\""+email+"\",\"password\":\"Fixture-password-2026\"}";
    }
    void runAs(Long id,Long business,String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedIdentity(id,business,role),null,List.of()));
    }
    @Test void adminCreatesEmployeeAndReadsSafeDetails() throws Exception {
        var response=mvc.perform(post("/api/v1/iam/users").header("Authorization",admin).contentType("application/json")
                .content(registration("  NEW@EXAMPLE.INVALID  ")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.businessId").value(businessId))
                .andExpect(jsonPath("$.email").value("new@example.invalid")).andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
        Number id=com.jayway.jsonpath.JsonPath.read(response.getContentAsString(),"$.id");
        assertEquals("/api/v1/iam/users/"+id,response.getHeader("Location"));
        assertFalse(response.getContentAsString().contains("Fixture-password-2026"));
        assertFalse(response.getContentAsString().contains("bcrypt"));
        String stored=jdbc.queryForObject("select password_hash from users where id=?",String.class,id.longValue());
        assertTrue(stored.startsWith("{bcrypt}$2b$12$"));
        assertTrue(new BCryptPasswordHashingService().matches("Fixture-password-2026",new PasswordHash(stored)));
        mvc.perform(get("/api/v1/iam/users/"+id).header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.intValue()))
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
    @Test void listingIsPagedOrderedAndRestrictedToOwnBusiness() throws Exception {
        mvc.perform(get("/api/v1/iam/users").param("page","0").param("size","1")
                .param("businessId",otherBusinessId.toString()).header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(adminId)).andExpect(jsonPath("$.items[0].businessId").value(businessId))
                .andExpect(jsonPath("$.hasNext").value(true));
        var body=mvc.perform(get("/api/v1/iam/users").header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("other@example.invalid"));assertFalse(body.contains("password"));
        mvc.perform(get("/api/v1/iam/users").param("size","101").header("Authorization",admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/iam/users").param("page","-1").header("Authorization",admin)).andExpect(status().isBadRequest());
    }
    @Test void employeesCannotPerformAnyAdministrativeOperation() throws Exception {
        mvc.perform(get("/api/v1/iam/users").header("Authorization",employee)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/iam/users/"+employeeId).header("Authorization",employee)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/iam/users").header("Authorization",employee).contentType("application/json")
                .content(registration("forbidden@example.invalid"))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/iam/users/"+employeeId+"/status").header("Authorization",employee)
                .contentType("application/json").content("{\"active\":false}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/iam/users")).andExpect(status().isUnauthorized());
    }
    @Test void crossBusinessAndUnknownIdsReturnSame404WithoutMutation() throws Exception {
        String cross=mvc.perform(get("/api/v1/iam/users/"+otherEmployeeId).header("Authorization",admin))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String absent=mvc.perform(get("/api/v1/iam/users/"+Long.MAX_VALUE).header("Authorization",admin))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertEquals(cross,absent);
        mvc.perform(patch("/api/v1/iam/users/"+otherEmployeeId+"/status").header("Authorization",admin)
                .contentType("application/json").content("{\"active\":false}")).andExpect(status().isNotFound());
        assertTrue(users.findByIdAndBusinessId(otherEmployeeId,otherBusinessId).orElseThrow().isEnabled());
    }
    @Test void activationDeactivationAndTokenEligibilityFollowCurrentState() throws Exception {
        mvc.perform(patch("/api/v1/iam/users/"+employeeId+"/status").header("Authorization",admin)
                .contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/v1/auth/me").header("Authorization",employee)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"employee@example.invalid\",\"password\":\"Fixture-password-2026\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/iam/users/"+employeeId+"/status").header("Authorization",admin)
                .contentType("application/json").content("{\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"employee@example.invalid\",\"password\":\"Fixture-password-2026\"}"))
                .andExpect(status().isOk());
    }
    @Test void adminsCannotBeDeactivatedThroughEmployeeManagement() throws Exception {
        mvc.perform(patch("/api/v1/iam/users/"+adminId+"/status").header("Authorization",admin)
                .contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isConflict());
        assertTrue(users.findByIdAndBusinessId(adminId,businessId).orElseThrow().isEnabled());
    }
    @Test void massAssignmentUnknownFieldsAndInvalidPayloadsAreRejected() throws Exception {
        for(String extra:List.of("\"role\":\"ADMIN\"","\"businessId\":"+otherBusinessId,"\"active\":false","\"passwordHash\":\"not-accepted\"")) {
            String body=registration("not-created@example.invalid").replace("}",","+extra+"}");
            mvc.perform(post("/api/v1/iam/users").header("Authorization",admin).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/v1/iam/users/"+employeeId+"/status").header("Authorization",admin)
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/iam/users").header("Authorization",admin).contentType("application/json")
                .content(registration("invalid"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/iam/users").header("Authorization",admin).contentType("application/json")
                .content(registration("overlength@example.invalid").replace("Fixture-password-2026","é".repeat(37))))
                .andExpect(status().isBadRequest());
        assertEquals(3,users.count());
    }
    @Test void duplicateEmailConflictsWithoutRevealingTheOtherBusiness() throws Exception {
        var response=mvc.perform(post("/api/v1/iam/users").header("Authorization",admin).contentType("application/json")
                .content(registration(" OTHER@EXAMPLE.INVALID ")))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertEquals("{\"message\":\"User could not be created\"}",response);
        assertEquals(3,users.count());
    }
    @Test void applicationLayerRejectsEmployeeAndForgedOrStaleAdminIdentity() {
        runAs(employeeId,businessId,"EMPLOYEE");
        var command=new RegisterEmployeeCommand("Fixture","denied@example.invalid","Fixture-password-2026".toCharArray());
        assertThrows(IamAccessDeniedException.class,() -> commands.handle(command));
        assertEquals('\0',command.password()[0]);
        assertThrows(IamAccessDeniedException.class,() -> queries.handle(new ListBusinessUsersQuery(0,20)));
        runAs(employeeId,businessId,"ADMIN");
        assertThrows(IamAccessDeniedException.class,() -> commands.handle(new ChangeEmployeeStatusCommand(employeeId,false)));
        runAs(adminId,otherBusinessId,"ADMIN");
        assertThrows(IamAccessDeniedException.class,() -> queries.handle(new GetManagedUserByIdQuery(otherEmployeeId)));
        runAs(adminId,businessId,"ADMIN"); jdbc.update("update businesses set active=false where id=?",businessId);
        assertThrows(IamAccessDeniedException.class,() -> queries.handle(new ListBusinessUsersQuery(0,20)));
        jdbc.update("update businesses set active=true where id=?",businessId);
        jdbc.update("update users set active=false where id=?",adminId);
        assertThrows(IamAccessDeniedException.class,() -> queries.handle(new ListBusinessUsersQuery(0,20)));
    }
    @Test void deactivatedAdminOrBusinessCannotUsePreviouslyIssuedToken() throws Exception {
        jdbc.update("update users set active=false where id=?",adminId);
        mvc.perform(get("/api/v1/iam/users").header("Authorization",admin)).andExpect(status().isUnauthorized());
        jdbc.update("update users set active=true where id=?",adminId);
        jdbc.update("update businesses set active=false where id=?",businessId);
        mvc.perform(post("/api/v1/iam/users").header("Authorization",admin).contentType("application/json")
                .content(registration("inactive@example.invalid"))).andExpect(status().isUnauthorized());
        assertEquals(3,users.count());
    }
    @Test void otherRoutesDeleteAndProvisioningStayDeniedAndCorsAllowsPatch() throws Exception {
        mvc.perform(delete("/api/v1/categories/1").header("Authorization",admin)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/iam/users/"+employeeId).header("Authorization",admin)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/iam/users/provision").header("Authorization",admin)).andExpect(status().isForbidden());
        mvc.perform(options("/api/v1/iam/users/"+employeeId+"/status").header("Origin","http://localhost:4200")
                .header("Access-Control-Request-Method","PATCH").header("Access-Control-Request-Headers","authorization,content-type"))
                .andExpect(status().isOk());
    }
}
