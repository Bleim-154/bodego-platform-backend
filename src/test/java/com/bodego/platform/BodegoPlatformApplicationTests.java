package com.bodego.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class BodegoPlatformApplicationTests {

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6")
			.withDatabaseName("bodego_hibernate_validation");

	@DynamicPropertySource
	static void containerProperties(DynamicPropertyRegistry registry) {
		registry.add("bodego.security.jwt.secret-base64", () -> java.util.Base64.getEncoder().encodeToString(java.security.SecureRandom.getSeed(32)));
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.flyway.enabled", () -> true);
	}

	@Test
	void contextLoads() {
	}

}
