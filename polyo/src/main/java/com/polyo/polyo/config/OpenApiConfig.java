package com.polyo.polyo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI: /swagger-ui.html, 문서 JSON: /v3/api-docs/{group} */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {
    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI polyoOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Polyo API").version("v1"))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    // 그룹을 하나라도 정의하면 기본 문서가 사라지므로 전체 API 그룹을 따로 둔다.
    @Bean
    public GroupedOpenApi allOpenApi() {
        return GroupedOpenApi.builder().group("all").pathsToMatch("/api/**").build();
    }
}
