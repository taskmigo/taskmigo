package io.taskmigo.web.api.v0;

import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiV0Configuration {

    @Bean
    GroupedOpenApi v0OpenApi() {
        return GroupedOpenApi.builder()
            .group("v0")
            .pathsToMatch("/api/v0/**")
            .addOpenApiCustomizer(openApi ->
                openApi.info(
                    new Info()
                        .title("Taskmigo API")
                        .version("v0")
                        .description("Taskmigo resource and project management API")
                )
            )
            .build();
    }
}
