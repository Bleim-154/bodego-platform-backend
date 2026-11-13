package com.bodego.platform.iam.infrastructure;

import com.bodego.platform.iam.domain.model.aggregates.*;
import com.bodego.platform.iam.domain.model.valueobjects.*;
import com.bodego.platform.iam.infrastructure.hashing.BCryptPasswordHashingService;
import com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Testcontainers
class AuthenticationHttpTests {
    static final java.util.concurrent.atomic.AtomicLong RATE_TIME=new java.util.concurrent.atomic.AtomicLong();
    @org.springframework.boot.test.context.TestConfiguration
    static class AdmissionClock {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        com.bodego.platform.iam.infrastructure.security.login.LoginRateLimiter testLoginLimiter(
                com.bodego.platform.iam.infrastructure.security.login.LoginRateLimitPolicy policy) {
            return new com.bodego.platform.iam.infrastructure.security.login.LoginRateLimiter(policy,RATE_TIME::get);
        }
    }
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    BCryptPasswordHashingService hashing;
    static final byte[] KEY=java.security.SecureRandom.getSeed(64);
    static final PasswordHash HASH=new BCryptPasswordHashingService().hash("Fixture-password-2026");
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("bodego_auth_http_isolated");
    @DynamicPropertySource static void isolated(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",POSTGRES::getUsername);
        registry.add("spring.datasource.password",POSTGRES::getPassword);
        registry.add("spring.flyway.enabled",() -> true);
        registry.add("bodego.security.jwt.secret-base64",() -> Base64.getEncoder().encodeToString(KEY));
        registry.add("bodego.security.cors.allowed-origins",() -> "http://localhost:4200");
    }
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired BusinessRepository businesses;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.bodego.platform.iam.application.internal.outboundservices.tokens.AccessTokenService tokens;
    @Autowired org.springframework.context.ApplicationContext context;
    Long userId,businessId;
    @BeforeEach void fixture() {
        RATE_TIME.addAndGet(Duration.ofMinutes(1).toNanos());
        org.mockito.Mockito.clearInvocations(hashing);
        assertNotEquals("bodego_db",jdbc.queryForObject("select current_database()",String.class));
        jdbc.update("delete from users"); jdbc.update("delete from businesses");
        businessId=businesses.saveAndFlush(new Business("Fixture shop","Fixture address","12345")).getId();
        userId=users.saveAndFlush(new User(businessId,"Fixture user",new EmailAddress("fixture@example.invalid"),HASH,UserRole.EMPLOYEE)).getId();
    }
    @Test void normalizedExistingAndAbsentAccountsHaveSameQuotaAndLimitedRequestsSkipBcrypt() throws Exception {
        for(String email:List.of("fixture@example.invalid","missing@example.invalid")) {
            for(int i=0;i<5;i++) {
                String variant=i%2==0?email:" "+email.toUpperCase(Locale.ROOT)+" ";
                mvc.perform(post("/api/v1/auth/login").with(request->{request.setRemoteAddr("192.0.2."+(email.startsWith("fixture")?1:2));return request;})
                        .header("X-Forwarded-For", "198.51.100."+i).header("Forwarded","for=198.51.100."+i)
                        .contentType("application/json").content("{\"email\":\""+variant+"\",\"password\":\"wrong-fixture\"}"))
                        .andExpect(status().isUnauthorized()).andExpect(content().json("{\"message\":\"Invalid credentials\"}"));
            }
            mvc.perform(post("/api/v1/auth/login").with(request->{request.setRemoteAddr("203.0.113.9");return request;})
                    .contentType("application/json").content("{\"email\":\""+email+"\",\"password\":\"Fixture-password-2026\"}"))
                    .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"))
                    .andExpect(header().string("Cache-Control","no-store"))
                    .andExpect(content().json("{\"message\":\"Too many requests\"}"));
        }
        org.mockito.Mockito.verify(hashing,org.mockito.Mockito.times(10)).matches(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        RATE_TIME.addAndGet(Duration.ofMinutes(1).toNanos());
        login(); // No durable lock or mutation: correct credentials work after expiry.
        org.mockito.Mockito.verify(hashing,org.mockito.Mockito.times(11)).matches(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
    }
    @Test void forwardedHeadersCannotBypassTheSocketPeerLimit() throws Exception {
        for(int i=0;i<31;i++) {
            mvc.perform(post("/api/v1/auth/login").header("X-Forwarded-For","198.51.100."+i)
                    .header("X-Real-IP","203.0.113."+i).header("Forwarded","for=198.51.100."+i)
                    .contentType("application/json").content("{\"email\":\"absent"+i+"@example.invalid\",\"password\":\"fixture\"}"))
                    .andExpect(status().is(i<30?401:429));
        }
        org.mockito.Mockito.verify(hashing,org.mockito.Mockito.times(30)).matches(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
    }
    String login() throws Exception {
        var response=mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"  FIXTURE@EXAMPLE.INVALID  \",\"password\":\"Fixture-password-2026\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900)).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(cookie().doesNotExist("JSESSIONID")).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response,"$.accessToken");
    }
    String signed(JWSAlgorithm algorithm,byte[] key,String issuer,Instant iat,Instant exp,Long business,String role) throws Exception {
        var claims=new JWTClaimsSet.Builder().issuer(issuer).subject(userId.toString()).issueTime(Date.from(iat))
                .expirationTime(Date.from(exp)).claim("businessId",business).claim("role",role).build();
        var jwt=new SignedJWT(new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT).build(),claims);
        jwt.sign(new MACSigner(key)); return jwt.serialize();
    }
    String token() throws Exception {
        var now=Instant.now().minusSeconds(1);
        return signed(JWSAlgorithm.HS256,KEY,"bodego-api",now,now.plusSeconds(600),businessId,"EMPLOYEE");
    }
    void rejected(String token) throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Unauthorized"));
    }
    @Test void loginAndMeExposeOnlyIdentity() throws Exception {
        String token=login();
        var jwt=SignedJWT.parse(token);
        assertEquals(JWSAlgorithm.HS256,jwt.getHeader().getAlgorithm());
        assertEquals(Set.of("sub","businessId","role","iss","iat","exp"),jwt.getJWTClaimsSet().getClaims().keySet());
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.businessId").value(businessId)).andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist());
    }
    @Test void wrongPasswordAndMissingEmailHaveSameGenericResponse() throws Exception {
        String first=mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"fixture@example.invalid\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String second=mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"absent@example.invalid\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertEquals("{\"message\":\"Invalid credentials\"}",first); assertEquals(first,second);
    }
    @Test void inactiveUserCannotLogin() throws Exception {
        jdbc.update("update users set active=false where id=?",userId); deniedLogin();
    }
    @Test void inactiveBusinessCannotLogin() throws Exception {
        jdbc.update("update businesses set active=false where id=?",businessId); deniedLogin();
    }
    void deniedLogin() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"fixture@example.invalid\",\"password\":\"Fixture-password-2026\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Invalid credentials"));
    }
    @Test void invalidRequestsReturn400WithoutEchoingInput() throws Exception {
        for(String body:List.of("{}","{bad-json","{\"email\":\"invalid\",\"password\":\"secret-input\"}",
                "{\"email\":\"fixture@example.invalid\",\"password\":\"\"}")) {
            mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid request"));
        }
    }
    @Test void validTokenAndNoTokenHaveIsolatedContexts() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+token())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        assertNull(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
    }
    @Test void businessIdOrRoleFromClientDoesNotEstablishIdentity() throws Exception {
        String response=mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"fixture@example.invalid\",\"password\":\"Fixture-password-2026\",\"businessId\":999,\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String issued=com.jayway.jsonpath.JsonPath.read(response,"$.accessToken");
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+issued))
                .andExpect(status().isOk()).andExpect(jsonPath("$.businessId").value(businessId))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
        rejected(signed(JWSAlgorithm.HS256,KEY,"bodego-api",Instant.now().minusSeconds(1),Instant.now().plusSeconds(300),999L,"EMPLOYEE"));
        rejected(signed(JWSAlgorithm.HS256,KEY,"bodego-api",Instant.now().minusSeconds(1),Instant.now().plusSeconds(300),businessId,"ADMIN"));
    }
    @Test void expiredTokenRejected() throws Exception {
        rejected(signed(JWSAlgorithm.HS256,KEY,"bodego-api",Instant.now().minusSeconds(100),Instant.now().minusSeconds(1),businessId,"EMPLOYEE"));
    }
    @Test void wrongSignatureRejected() throws Exception {
        rejected(signed(JWSAlgorithm.HS256,java.security.SecureRandom.getSeed(64),"bodego-api",Instant.now(),Instant.now().plusSeconds(300),businessId,"EMPLOYEE"));
    }
    @Test void unexpectedAlgorithmAndNoneRejected() throws Exception {
        rejected(signed(JWSAlgorithm.HS384,KEY,"bodego-api",Instant.now(),Instant.now().plusSeconds(300),businessId,"EMPLOYEE"));
        rejected(new PlainJWT(new JWTClaimsSet.Builder().subject(userId.toString()).build()).serialize());
        rejected("not-a-jwt");
    }
    @Test void wrongIssuerFutureIatAndExcessLifetimeRejected() throws Exception {
        rejected(signed(JWSAlgorithm.HS256,KEY,"other",Instant.now(),Instant.now().plusSeconds(300),businessId,"EMPLOYEE"));
        rejected(signed(JWSAlgorithm.HS256,KEY,"bodego-api",Instant.now().plusSeconds(100),Instant.now().plusSeconds(300),businessId,"EMPLOYEE"));
        rejected(signed(JWSAlgorithm.HS256,KEY,"bodego-api",Instant.now(),Instant.now().plusSeconds(3600),businessId,"EMPLOYEE"));
    }
    @Test void deactivationTakesEffectOnNextRequest() throws Exception {
        String token=token();
        jdbc.update("update users set active=false where id=?",userId); rejected(token);
        jdbc.update("update users set active=true where id=?",userId);
        jdbc.update("update businesses set active=false where id=?",businessId); rejected(token);
    }
    @Test void endpointsDeniedByDefaultEvenWithValidIdentity() throws Exception {
        mvc.perform(get("/api/v1/unmapped")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/unmapped").header("Authorization","Bearer "+token()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("Forbidden"));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/register").header("Authorization","Bearer "+token())).andExpect(status().isForbidden());
    }
    @Test void corsAllowsOnlyConfiguredOriginsAndHeaders() throws Exception {
        mvc.perform(options("/api/v1/auth/login").header("Origin","http://localhost:4200")
                .header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","content-type,authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:4200"));
        mvc.perform(options("/api/v1/auth/login").header("Origin","https://untrusted.invalid").header("Access-Control-Request-Method","POST"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(get("/api/v1/auth/me").header("Origin","https://untrusted.invalid").header("Authorization","Bearer "+token()))
                .andExpect(status().isForbidden());
    }
    @Test void basicAndSessionAreNotAuthenticationMechanisms() throws Exception {
        assertTrue(context.getBeansOfType(org.springframework.security.core.userdetails.UserDetailsService.class).isEmpty());
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Basic Zm9vOmJhcg==")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").cookie(new jakarta.servlet.http.Cookie("JSESSIONID","arbitrary"))).andExpect(status().isUnauthorized());
    }
    @Test void concurrentRequestsDoNotShareIdentityOrRetainContext() throws Exception {
        var otherBusiness=businesses.saveAndFlush(new Business("Other fixture","Other address","67890"));
        var other=users.saveAndFlush(new User(otherBusiness.getId(),"Other fixture",new EmailAddress("other@example.invalid"),HASH,UserRole.EMPLOYEE));
        var first=new com.bodego.platform.shared.application.security.AuthenticatedIdentity(userId,businessId,"EMPLOYEE");
        var second=new com.bodego.platform.shared.application.security.AuthenticatedIdentity(other.getId(),otherBusiness.getId(),"EMPLOYEE");
        String firstToken=tokens.issue(first).accessToken(),secondToken=tokens.issue(second).accessToken();
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var jobs=new ArrayList<java.util.concurrent.Callable<Void>>();
            for(int i=0;i<12;i++) {
                var expected=i%2==0?first:second; String bearer=i%2==0?firstToken:secondToken;
                jobs.add(() -> {
                    mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+bearer))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(expected.userId()))
                            .andExpect(jsonPath("$.businessId").value(expected.businessId()));
                    assertNull(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
                    mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
                    return null;
                });
            }
            for(var result:executor.invokeAll(jobs)) result.get(20,java.util.concurrent.TimeUnit.SECONDS);
        }
    }
    @Test void missingClaimsAndFutureNotBeforeAreRejected() throws Exception {
        for(String missing:List.of("iat","exp","sub","businessId","role")) {
            Map<String,Object> claims=new HashMap<>(); claims.put("iss","bodego-api"); claims.put("sub",userId.toString());
            claims.put("iat",Date.from(Instant.now().minusSeconds(1))); claims.put("exp",Date.from(Instant.now().plusSeconds(300)));
            claims.put("businessId",businessId); claims.put("role","EMPLOYEE"); claims.remove(missing);
            var builder=new JWTClaimsSet.Builder(); claims.forEach(builder::claim);
            var jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),builder.build()); jwt.sign(new MACSigner(KEY));
            rejected(jwt.serialize());
        }
        var claims=new JWTClaimsSet.Builder().issuer("bodego-api").subject(userId.toString())
                .issueTime(Date.from(Instant.now())).expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .notBeforeTime(Date.from(Instant.now().plusSeconds(60))).claim("businessId",businessId).claim("role","EMPLOYEE").build();
        var jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),claims); jwt.sign(new MACSigner(KEY)); rejected(jwt.serialize());
    }
}
