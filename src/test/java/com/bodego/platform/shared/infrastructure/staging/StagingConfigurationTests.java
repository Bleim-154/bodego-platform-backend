package com.bodego.platform.shared.infrastructure.staging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;

class StagingConfigurationTests {
    MockEnvironment valid() {
        return new MockEnvironment().withProperty("spring.datasource.url","jdbc:postgresql://db.example.invalid:5432/bodego_staging")
                .withProperty("spring.datasource.username","bodego_runtime").withProperty("spring.datasource.password","private-fixture")
                .withProperty("bodego.security.jwt.secret-base64",Base64.getEncoder().encodeToString(new byte[32]))
                .withProperty("bodego.security.jwt.issuer","bodego-api").withProperty("bodego.security.jwt.ttl","PT15M")
                .withProperty("bodego.security.cors.allowed-origins","https://front.example.invalid")
                .withProperty("spring.jpa.hibernate.ddl-auto","validate").withProperty("spring.flyway.enabled","false");
    }
    @Test void completeExternalConfigurationIsAcceptedWithoutOpeningAConnection() {
        assertDoesNotThrow(()->StagingConfiguration.validate(valid()));
    }
    @Test void stagingTemplateBindsDynamicPortPublicSchemaAndVerifyingTls() throws Exception {
        var properties=new java.util.Properties();
        try(var input=new org.springframework.core.io.ClassPathResource("application-staging.properties").getInputStream()) {properties.load(input);}
        var environment=valid().withProperty("PORT","18123");
        environment.getPropertySources().addLast(new org.springframework.core.env.PropertiesPropertySource("staging-template",properties));
        assertEquals("18123",environment.getProperty("server.port"));
        assertEquals("public",environment.getProperty("spring.datasource.hikari.schema"));
        assertEquals("verify-full",environment.getProperty("spring.datasource.hikari.data-source-properties.sslmode"));
        assertEquals("org.postgresql.ssl.DefaultJavaSSLFactory",environment.getProperty("spring.datasource.hikari.data-source-properties.sslfactory"));
    }
    @Test void absentWeakAndMalformedSecretsFailWithSafeCodes() {
        for(String key:new String[]{"","private-fixture!",Base64.getEncoder().encodeToString(new byte[16])}) {
            var error=assertThrows(IllegalStateException.class,()->StagingConfiguration.validate(valid().withProperty("bodego.security.jwt.secret-base64",key)));
            assertEquals("STAGING_JWT_CONFIG",error.getMessage()); assertNull(error.getCause());
        }
    }
    @Test void absentDatabaseCredentialsEmbeddedCredentialsAndOriginalDatabaseAreRejected() {
        for(String url:new String[]{"","jdbc:postgresql://localhost:5432/bodego_db",
                "jdbc:postgresql://private:fixture@db.example.invalid/staging","jdbc:postgresql://db.example.invalid/staging?password=private-fixture"})
            assertEquals("STAGING_DB_CONFIG",assertThrows(IllegalStateException.class,
                    ()->StagingConfiguration.validate(valid().withProperty("spring.datasource.url",url))).getMessage());
        assertThrows(IllegalStateException.class,()->StagingConfiguration.validate(valid().withProperty("spring.datasource.password","")));
        assertThrows(IllegalStateException.class,()->StagingConfiguration.validate(valid().withProperty("spring.datasource.username","postgres")));
    }
    @Test void corsLifetimeAndSchemaPoliciesFailClosed() {
        for(String origins:new String[]{"","*","http://localhost:4200","https://front.example.invalid/path"})
            assertThrows(IllegalStateException.class,()->StagingConfiguration.validate(valid().withProperty("bodego.security.cors.allowed-origins",origins)));
        assertThrows(IllegalStateException.class,()->StagingConfiguration.validate(valid().withProperty("bodego.security.jwt.ttl","PT16M")));
        assertThrows(IllegalStateException.class,()->StagingConfiguration.validate(valid().withProperty("spring.flyway.enabled","true")));
        assertThrows(IllegalStateException.class,()->StagingConfiguration.validate(valid().withProperty("spring.jpa.hibernate.ddl-auto","update")));
    }
    @Test void missingJwtStopsTheContextBeforeAnyDatasourceCanBeCreated() {
        var called=new AtomicBoolean();
        var environment=valid().withProperty("bodego.security.jwt.secret-base64","");
        new ApplicationContextRunner().withInitializer(context->{
            context.getEnvironment().setActiveProfiles("staging");
            context.getEnvironment().getPropertySources().addFirst(environment.getPropertySources().get("mockProperties"));
        }).withUserConfiguration(StagingConfiguration.class).withBean(DataSource.class,()->{
            called.set(true);throw new AssertionError("No datasource must be created");
        }).run(context->{
            Throwable failure=context.getStartupFailure(); assertNotNull(failure);
            while(failure.getCause()!=null) failure=failure.getCause();
            assertEquals("STAGING_JWT_CONFIG",failure.getMessage()); assertFalse(called.get());
        });
    }
}
