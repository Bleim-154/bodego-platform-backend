package com.bodego.platform.shared.infrastructure.staging;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: requires the two locally built images. Never pulls an application image or uses a host DB. */
class StagingContainerIT {
    @Test void imagesServeDynamicPortsWithRuntimePrivilegesAndSpaRouting() throws Exception {
        try(var network=Network.newNetwork(); var postgres=StagingHttpTests.POSTGRES.withNetwork(network).withNetworkAliases("db")) {
            postgres.start();
            var values=new HashMap<String,String>();
            StagingHttpTests.isolated((name,value)->values.put(name,value.get().toString()));
            values.put("DB_JDBC_URL","jdbc:postgresql://db:5432/"+postgres.getDatabaseName());
            values.put("PORT","18080");
            try(var backend=new GenericContainer<>("bodego-backend:staging-rehearsal").withImagePullPolicy(image->false)
                    .withNetwork(network).withEnv(values).withExposedPorts(18080)
                    .waitingFor(Wait.forHttp("/health/ready").forStatusCode(200)).withStartupTimeout(Duration.ofMinutes(2));
                var frontend=new GenericContainer<>("bodego-frontend:staging-rehearsal").withImagePullPolicy(image->false)
                    .withEnv("PORT","18081").withExposedPorts(18081)
                    .waitingFor(Wait.forHttp("/products").forStatusCode(200))) {
                backend.start(); frontend.start();
                assertEquals("10001:10001",backend.getContainerInfo().getConfig().getUser());
                assertFalse(Set.of("","root","0").contains(frontend.getContainerInfo().getConfig().getUser()));
                String api="http://"+backend.getHost()+":"+backend.getMappedPort(18080);
                assertEquals("{\"status\":\"UP\"}",get(api+"/health/ready").body());
                assertEquals(401,get(api+"/api/v1/auth/me").statusCode());
                assertEquals(401,get(api+"/actuator/env").statusCode());
                String ui="http://"+frontend.getHost()+":"+frontend.getMappedPort(18081);
                var page=get(ui+"/inventory/movements"); assertEquals(200,page.statusCode());
                var matcher=java.util.regex.Pattern.compile("src=\"(main-[^\"]+\\.js)\"").matcher(page.body());
                assertTrue(matcher.find());
                var script=get(ui+"/"+matcher.group(1)); assertEquals(200,script.statusCode());
                assertTrue(script.body().contains("https://api.staging.example.invalid/api/v1"));
                assertFalse(script.body().contains("localhost")); assertFalse(script.body().contains("127.0.0.1"));
            }
        }
    }

    @Test void backendImageWithoutSecretsFailsBeforeDatasourceCreationEvenWithoutNetworking() {
        var logs=new StringBuilder();
        try(var backend=new GenericContainer<>("bodego-backend:staging-rehearsal").withImagePullPolicy(image->false)
                .withNetworkMode("none").withLogConsumer(frame->logs.append(frame.getUtf8String()))
                .withStartupAttempts(1).withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofSeconds(40)))) {
            assertThrows(org.testcontainers.containers.ContainerLaunchException.class,backend::start);
            assertTrue(logs.toString().contains("STAGING_DB_CONFIG"));
            assertFalse(logs.toString().contains("HikariPool-1 - Starting"));
        }
    }

    private HttpResponse<String> get(String url) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
