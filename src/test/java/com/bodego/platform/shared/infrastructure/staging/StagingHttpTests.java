package com.bodego.platform.shared.infrastructure.staging;

import com.bodego.platform.shared.interfaces.rest.StagingHealthController;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@ActiveProfiles("staging")
@Testcontainers
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class StagingHttpTests {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("bodego_staging_isolated");
    static final String KEY=Base64.getEncoder().encodeToString(java.security.SecureRandom.getSeed(32));
    @DynamicPropertySource static void isolated(DynamicPropertyRegistry registry) throws Exception {
        assertNotEquals(5432,POSTGRES.getMappedPort(5432));
        try(var connection=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());var sql=connection.createStatement()) {
            assertNotEquals("bodego_db",POSTGRES.getDatabaseName());
            sql.execute("CREATE ROLE bodego_migrator LOGIN PASSWORD 'fixture-migrator-only' NOSUPERUSER NOCREATEDB NOCREATEROLE");
            sql.execute("CREATE ROLE bodego_runtime LOGIN PASSWORD 'fixture-runtime-only' NOSUPERUSER NOCREATEDB NOCREATEROLE");
            sql.execute("ALTER DATABASE bodego_staging_isolated OWNER TO bodego_migrator");
            sql.execute("ALTER SCHEMA public OWNER TO bodego_migrator");
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),"bodego_migrator","fixture-migrator-only")
                .locations("classpath:db/migration").baselineOnMigrate(false).cleanDisabled(true).load().migrate();
        try(var connection=DriverManager.getConnection(POSTGRES.getJdbcUrl(),"bodego_migrator","fixture-migrator-only");var sql=connection.createStatement()) {
            sql.execute("SET bodego.expected_database='bodego_staging_isolated'");
            sql.execute("SET bodego.staging_grants_approval='staging-grants-reviewed-v1'");
            sql.execute(Files.readString(Path.of("scripts/staging/grant-runtime.sql")));
        }
        registry.add("DB_JDBC_URL",POSTGRES::getJdbcUrl);
        registry.add("DB_USERNAME",()->"bodego_runtime");
        registry.add("DB_PASSWORD",()->"fixture-runtime-only");
        registry.add("DB_SSLMODE",()->"disable"); // Owned local container only; Render template uses verify-full.
        registry.add("JWT_SECRET_BASE64",()->KEY);
        registry.add("CORS_ALLOWED_ORIGINS",()->"https://front.example.invalid");
    }
    @Autowired MockMvc mvc;
    @Autowired DataSource source;
    @Autowired org.springframework.core.env.Environment environment;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.BusinessRepository businesses;
    @Autowired com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.UserRepository users;
    @Autowired com.bodego.platform.catalog.infrastructure.persistence.jpa.repositories.CategoryRepository categories;
    @Autowired com.bodego.platform.catalog.infrastructure.persistence.jpa.repositories.ProductRepository products;

    @Test void runtimeRoleSupportsAnAuthorizedStockTransactionIncludingCountLocksAndAudit() throws Exception {
        // Explicit synthetic fixtures in this Testcontainer, never a startup initializer.
        var business=businesses.saveAndFlush(new com.bodego.platform.iam.domain.model.aggregates.Business("Staging flow fixture","Fixture","1"));
        var hash=new com.bodego.platform.iam.infrastructure.hashing.BCryptPasswordHashingService().hash("Fixture-only-password");
        var user=users.saveAndFlush(new com.bodego.platform.iam.domain.model.aggregates.User(business.getId(),"Fixture",
                new com.bodego.platform.iam.domain.model.valueobjects.EmailAddress("staging@example.invalid"),hash,
                com.bodego.platform.iam.domain.model.valueobjects.UserRole.ADMIN));
        var category=categories.saveAndFlush(new com.bodego.platform.catalog.domain.model.aggregates.Category(business.getId(),"Fixture",null));
        var product=products.saveAndFlush(new com.bodego.platform.catalog.domain.model.aggregates.Product(business.getId(),category.getId(),"Fixture",null,java.math.BigDecimal.ONE,0));
        var login=mvc.perform(post("/api/v1/auth/login").header("Origin","https://front.example.invalid").contentType("application/json")
                .content("{\"email\":\"staging@example.invalid\",\"password\":\"Fixture-only-password\"}"))
                .andExpect(status().isOk()).andReturn();
        String token="Bearer "+com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(),"$.accessToken");
        mvc.perform(get("/api/v1/auth/me").header("Authorization",token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(business.getId()))
                .andExpect(jsonPath("$.role").value("ADMIN")).andExpect(jsonPath("$.passwordHash").doesNotExist());
        var lotResult=mvc.perform(post("/api/v1/lots").header("Authorization",token).contentType("application/json")
                .content("{\"businessId\":"+business.getId()+",\"productId\":"+product.getId()+",\"batchNumber\":\"Fixture\",\"quantity\":0}"))
                .andExpect(status().isCreated()).andReturn();
        Number lot=com.jayway.jsonpath.JsonPath.read(lotResult.getResponse().getContentAsString(),"$.id");
        mvc.perform(post("/api/v1/inventory-movements").header("Authorization",token).contentType("application/json")
                .content("{\"businessId\":"+business.getId()+",\"lotId\":"+lot+",\"type\":\"ENTRY\",\"quantity\":10,\"reason\":\"Fixture\"}"))
                .andExpect(status().isCreated());
        var countResult=mvc.perform(post("/api/v1/physical-counts").header("Authorization",token).contentType("application/json")
                .content("{\"businessId\":"+business.getId()+",\"lotId\":"+lot+",\"countedQuantity\":7}"))
                .andExpect(status().isCreated()).andReturn();
        Number count=com.jayway.jsonpath.JsonPath.read(countResult.getResponse().getContentAsString(),"$.id");
        String reason="a".repeat(500);
        mvc.perform(post("/api/v1/stock-adjustments").header("Authorization",token).contentType("application/json")
                .content("{\"businessId\":"+business.getId()+",\"physicalCountId\":"+count+",\"reason\":\""+reason+"\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.reason").value(reason));
        assertEquals(7,jdbc.queryForObject("SELECT quantity FROM public.lots WHERE id=?",Integer.class,lot));
        assertEquals(4,jdbc.queryForObject("SELECT count(*) FROM public.audit_logs WHERE business_id=? AND user_id=?",Integer.class,business.getId(),user.getId()));
    }

    @Test void readinessIsReadOnlyPublicMinimalAndDoesNotRequireAdministrativeCredentials() throws Exception {
        mvc.perform(get("/health/live")).andExpect(status().isOk()).andExpect(content().string("{\"status\":\"UP\"}"));
        mvc.perform(get("/health/ready")).andExpect(status().isOk()).andExpect(content().string("{\"status\":\"UP\"}"))
                .andExpect(header().string("Cache-Control","no-store"));
        assertEquals("false",environment.getProperty("spring.flyway.enabled"));
        assertEquals("validate",environment.getProperty("spring.jpa.hibernate.ddl-auto"));
    }
    @Test void stagingCorsAllowsOnlyTheExplicitHttpsFrontend() throws Exception {
        mvc.perform(options("/api/v1/auth/login").header("Origin","https://front.example.invalid")
                .header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","Content-Type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","https://front.example.invalid"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mvc.perform(options("/api/v1/auth/login").header("Origin","https://foreign.example.invalid")
                .header("Access-Control-Request-Method","POST")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(post("/health/ready")).andExpect(status().isForbidden());
    }
    @Test void runtimeCannotAlterSchemaHistoryOrSequencesButCanUseApplicationDml() throws Exception {
        try(var connection=source.getConnection();var sql=connection.createStatement()) {
            try(var result=sql.executeQuery("SELECT current_user")) {assertTrue(result.next());assertEquals("bodego_runtime",result.getString(1));}
            assertThrows(SQLException.class,()->sql.execute("CREATE TABLE public.forbidden_fixture(id bigint)"));
            assertThrows(SQLException.class,()->sql.execute("ALTER TABLE public.products ADD forbidden_fixture integer"));
            assertThrows(SQLException.class,()->sql.execute("DELETE FROM public.users"));
            assertThrows(SQLException.class,()->sql.execute("TRUNCATE public.products"));
            assertThrows(SQLException.class,()->sql.execute("SELECT * FROM public.flyway_schema_history"));
            assertThrows(SQLException.class,()->sql.execute("SELECT setval('public.products_id_seq',1000)"));
            connection.setAutoCommit(false);
            sql.executeQuery("SELECT id FROM public.physical_counts FOR UPDATE").close();
            sql.execute("INSERT INTO public.businesses(name,address,phone,active,created_at,updated_at) VALUES('Isolated fixture','Fixture','1',true,now(),now())");
            sql.execute("UPDATE public.businesses SET active=false WHERE name='Isolated fixture'");
            connection.rollback();
        }
    }
    @Test void failedReadinessDoesNotExposeDriverErrors() {
        DataSource failing=org.mockito.Mockito.mock(DataSource.class,org.mockito.Mockito.withSettings().mockMaker(org.mockito.MockMakers.PROXY));
        try {org.mockito.Mockito.when(failing.getConnection()).thenThrow(new SQLException("PRIVATE_FIXTURE"));}
        catch(SQLException impossible) {throw new AssertionError(impossible);}
        var response=new StagingHealthController(failing).ready();
        assertEquals(503,response.getStatusCode().value());
        assertEquals(Map.of("status","OUT_OF_SERVICE"),response.getBody());
    }

    @Test void operationalGrantsRefuseAConnectionWithoutExplicitDestinationApproval() throws Exception {
        try(var connection=DriverManager.getConnection(POSTGRES.getJdbcUrl(),"bodego_migrator","fixture-migrator-only");var sql=connection.createStatement()) {
            var rejected=assertThrows(SQLException.class,()->sql.execute(Files.readString(Path.of("scripts/staging/grant-runtime.sql"))));
            assertTrue(rejected.getMessage().contains("STAGING_GRANTS_DESTINATION_NOT_APPROVED"));
        }
    }
}
