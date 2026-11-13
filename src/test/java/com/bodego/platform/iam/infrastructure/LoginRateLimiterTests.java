package com.bodego.platform.iam.infrastructure;

import com.bodego.platform.iam.domain.model.valueobjects.EmailAddress;
import com.bodego.platform.iam.infrastructure.security.login.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import javax.sql.DataSource;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterTests {
    LoginRateLimitPolicy policy(int origin,int account,int total,int keys,int concurrent) {
        return new LoginRateLimitPolicy(Duration.ofSeconds(60),origin,account,total,keys,concurrent);
    }
    EmailAddress email(String name) { return new EmailAddress(name+"@example.invalid"); }

    @Test void accountQuotaIsNormalizedIndependentOfOriginAndDoesNotExtendOnRejection() {
        var clock=new AtomicLong(); var limiter=new LoginRateLimiter(policy(30,2,100,100,4),clock::get);
        try(var permit=limiter.acquire("192.0.2.1",email("fixture"))) {}
        try(var permit=limiter.acquire("192.0.2.2",new EmailAddress(" FIXTURE@EXAMPLE.INVALID "))) {}
        clock.set(Duration.ofSeconds(59).toNanos());
        assertEquals(60,assertThrows(LoginRateLimitExceededException.class,()->limiter.acquire("192.0.2.3",email("fixture"))).retryAfter());
        clock.set(Duration.ofSeconds(60).toNanos());
        try(var permit=limiter.acquire("192.0.2.3",email("fixture"))) {}
        var fractional=new LoginRateLimiter(new LoginRateLimitPolicy(Duration.ofSeconds(1,1),1,1,1,2,1),()->0);
        try(var permit=fractional.acquire("192.0.2.1",email("fixture"))) {}
        assertEquals(2,assertThrows(LoginRateLimitExceededException.class,()->fractional.acquire("192.0.2.1",email("fixture"))).retryAfter());
    }
    @Test void socketQuotaCannotBeAvoidedByRotatingAccountsAndGlobalQuotaBoundsRotatingPeers() {
        var byPeer=new LoginRateLimiter(policy(2,5,10,100,4),()->0);
        for(int i=0;i<2;i++) try(var permit=byPeer.acquire("192.0.2.1",email("fixture"+i))) {}
        assertThrows(LoginRateLimitExceededException.class,()->byPeer.acquire("192.0.2.1",email("other")));
        var global=new LoginRateLimiter(policy(10,5,2,100,4),()->0);
        for(int i=0;i<2;i++) try(var permit=global.acquire("192.0.2."+i,email("fixture"+i))) {}
        assertThrows(LoginRateLimitExceededException.class,()->global.acquire("192.0.2.9",email("other")));
    }
    @Test void fullRegistryFailsClosedWithoutEvictingActiveLimitsAndRecoversAtExpiry() {
        var clock=new AtomicLong(); var limiter=new LoginRateLimiter(policy(30,2,100,2,4),clock::get);
        try(var permit=limiter.acquire("192.0.2.1",email("fixture"))) {}
        for(int i=0;i<10;i++) assertThrows(LoginRateLimitExceededException.class,()->limiter.acquire("192.0.2.2",email("other")));
        // Registry saturation does not consume existing keys' remaining allowance.
        try(var permit=limiter.acquire("192.0.2.1",email("fixture"))) {}
        assertThrows(LoginRateLimitExceededException.class,()->limiter.acquire("192.0.2.1",email("fixture")));
        clock.set(Duration.ofSeconds(60).toNanos());
        try(var permit=limiter.acquire("192.0.2.2",email("other"))) {}
    }
    @Test void concurrentAdmissionsAreAtomicAndNeverExceedAccountBudget() throws Exception {
        var limiter=new LoginRateLimiter(policy(100,5,100,100,32),()->0);
        var admitted=new AtomicInteger(); var denied=new AtomicInteger(); var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(16)) {
            var futures=new ArrayList<Future<?>>();
            for(int i=0;i<64;i++) futures.add(executor.submit(()->{
                try { start.await(); try(var permit=limiter.acquire("192.0.2.1",email("fixture"))) {admitted.incrementAndGet();} }
                catch(LoginRateLimitExceededException expected) {denied.incrementAndGet();}
                catch(InterruptedException failure) {Thread.currentThread().interrupt();throw new AssertionError(failure);}
            }));
            start.countDown(); for(var future:futures) future.get(10,TimeUnit.SECONDS);
        }
        assertEquals(5,admitted.get()); assertEquals(59,denied.get());
    }
    @Test void inFlightBoundIsReleasedOnFailureAndDoubleCloseIsHarmless() {
        var limiter=new LoginRateLimiter(policy(30,5,100,100,1),()->0);
        var first=limiter.acquire("192.0.2.1",email("fixture"));
        assertThrows(LoginRateLimitExceededException.class,()->limiter.acquire("192.0.2.2",email("other")));
        first.close(); first.close();
        try(var permit=limiter.acquire("192.0.2.2",email("other"))) {
            assertThrows(LoginRateLimitExceededException.class,()->limiter.acquire("192.0.2.3",email("third")));
        }
    }
    @Test void invalidPoliciesAndForwardedRewritingFailSafely() {
        for(String value:List.of("","PT0S","PT16M","private-invalid-value")) {
            var failure=assertThrows(IllegalStateException.class,()->LoginRateLimitPolicy.from(
                    new MockEnvironment().withProperty("bodego.security.login-rate-limit.window",value)));
            assertEquals("LOGIN_RATE_LIMIT_CONFIG",failure.getMessage()); assertNull(failure.getCause());
        }
        for(String key:List.of("origin-attempts","account-attempts","total-attempts","max-keys","max-concurrent"))
            for(String value:List.of("0","-1","100001","invalid")) assertThrows(IllegalStateException.class,
                    ()->LoginRateLimitPolicy.from(new MockEnvironment().withProperty("bodego.security.login-rate-limit."+key,value)));
        for(String strategy:List.of("native","framework")) assertThrows(IllegalStateException.class,
                ()->LoginRateLimitPolicy.from(new MockEnvironment().withProperty("server.forward-headers-strategy",strategy)));
    }
    @Test void invalidStagingPolicyStopsContextBeforeDatasourceCreation() {
        var connected=new AtomicBoolean();
        new ApplicationContextRunner().withPropertyValues("spring.profiles.active=staging",
                "bodego.security.login-rate-limit.account-attempts=0")
                .withUserConfiguration(LoginRateLimitConfiguration.class).withBean(DataSource.class,()->{
                    connected.set(true);throw new AssertionError("No datasource");
                }).run(context->{
                    Throwable failure=context.getStartupFailure(); assertNotNull(failure);
                    while(failure.getCause()!=null) failure=failure.getCause();
                    assertEquals("LOGIN_RATE_LIMIT_CONFIG",failure.getMessage()); assertFalse(connected.get());
                });
    }
    @Test void stagingInheritsConfigurableLimitsAndKeepsSocketPeerMetadata() throws Exception {
        var base=new Properties(); var staging=new Properties();
        // The test classpath deliberately shadows application.properties with a fail-closed datasource.
        // Inspect the production template explicitly; do not weaken the test datasource guard.
        try(var stream=java.nio.file.Files.newInputStream(java.nio.file.Path.of("src/main/resources/application.properties"))) {base.load(stream);}
        try(var stream=getClass().getResourceAsStream("/application-staging.properties")) {staging.load(stream);}
        var env=new MockEnvironment().withProperty("LOGIN_RATE_LIMIT_ACCOUNT_ATTEMPTS","2")
                .withProperty("LOGIN_RATE_LIMIT_WINDOW","PT30S");
        env.setActiveProfiles("staging");
        env.getPropertySources().addLast(new org.springframework.core.env.PropertiesPropertySource("staging",staging));
        env.getPropertySources().addLast(new org.springframework.core.env.PropertiesPropertySource("base",base));
        var configured=LoginRateLimitPolicy.from(env);
        assertEquals(2,configured.accountAttempts()); assertEquals(Duration.ofSeconds(30),configured.window());
        assertEquals(30,configured.originAttempts()); assertEquals(100,configured.totalAttempts());
        assertEquals(4096,configured.maxKeys()); assertEquals(4,configured.maxConcurrent());
        assertEquals("none",env.getProperty("server.forward-headers-strategy"));
    }
}
