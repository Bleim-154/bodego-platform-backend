package com.bodego.platform.shared.infrastructure.staging;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import java.net.URI;
import java.time.Duration;
import java.util.*;

/** Validate before singleton/datasource creation. Never include configuration values in errors. */
@Configuration
@Profile("staging")
public class StagingConfiguration {
    @Bean static BeanFactoryPostProcessor stagingConfigurationGuard(Environment environment) {
        return factory -> validate(environment);
    }

    static void validate(Environment env) {
        try {
            String url=env.getRequiredProperty("spring.datasource.url");
            if(!url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();
            var uri=URI.create(url.substring(5));
            String query=Objects.toString(uri.getRawQuery(),"");
            // TLS is configured separately. Do not let URL parameters override it or carry credentials.
            if(!query.isEmpty()) for(String parameter:query.split("&")) {
                String decoded=java.net.URLDecoder.decode(parameter,java.nio.charset.StandardCharsets.UTF_8);
                if(!decoded.equalsIgnoreCase("loggerLevel=OFF") && !decoded.equals("currentSchema=public"))
                    throw new IllegalArgumentException();
            }
            if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null
                    || uri.getPath()==null || uri.getPath().length()<2 || uri.getPath().equals("/bodego_db")
                    || env.getRequiredProperty("spring.datasource.username").isBlank()
                    || env.getRequiredProperty("spring.datasource.username").equalsIgnoreCase("postgres")
                    || env.getRequiredProperty("spring.datasource.password").isBlank()) throw new IllegalArgumentException();
        } catch(RuntimeException failure) { throw invalid("STAGING_DB_CONFIG"); }
        try {
            byte[] key=Base64.getDecoder().decode(env.getRequiredProperty("bodego.security.jwt.secret-base64"));
            boolean strong=key.length>=32; Arrays.fill(key,(byte)0);
            Duration ttl=Duration.parse(env.getRequiredProperty("bodego.security.jwt.ttl"));
            if(!strong || env.getRequiredProperty("bodego.security.jwt.issuer").isBlank()
                    || ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofMinutes(15))>0)
                throw new IllegalArgumentException();
        } catch(RuntimeException failure) { throw invalid("STAGING_JWT_CONFIG"); }
        try {
            String origins=env.getRequiredProperty("bodego.security.cors.allowed-origins");
            if(origins.isBlank()) throw new IllegalArgumentException();
            for(String origin:origins.split(",",-1)) {
                URI uri=URI.create(origin.strip());
                if(!"https".equals(uri.getScheme()) || uri.getHost()==null || origin.contains("*")
                        || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                        || !uri.getPath().isEmpty()) throw new IllegalArgumentException();
            }
        } catch(RuntimeException failure) { throw invalid("STAGING_CORS_CONFIG"); }
        if(!"validate".equals(env.getProperty("spring.jpa.hibernate.ddl-auto"))
                || !"false".equals(env.getProperty("spring.flyway.enabled")))
            throw invalid("STAGING_SCHEMA_POLICY");
    }

    private static IllegalStateException invalid(String code) { return new IllegalStateException(code); }
}
