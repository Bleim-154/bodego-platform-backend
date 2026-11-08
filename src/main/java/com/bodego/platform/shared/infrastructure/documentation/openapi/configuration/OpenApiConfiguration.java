package com.bodego.platform.shared.infrastructure.documentation.openapi.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI bodegoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("BodeGo API")
                        .description(
                                "REST API for inventory management " +
                                        "in bodegas and minimarkets."
                        )
                        .version("v1.0.0"));
    }
}
