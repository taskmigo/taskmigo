package io.taskmigo.openapi;

import io.swagger.v3.oas.models.info.Info;
import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.query.QuerySchemaView;
import java.util.List;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiGenerationConfiguration {

    @Bean
    GroupedOpenApi v0OpenApi(List<QuerySchemaView> schemas) {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(AuthorizationContext.class);
        return GroupedOpenApi.builder()
            .group("v0")
            .pathsToMatch("/api/v{version}/**")
            .addOperationCustomizer(new FilterByOpenApiCustomizer(schemas))
            .addOpenApiCustomizer(openApi ->
                openApi.info(new Info().title("Taskmigo API").version("v0").description("Taskmigo API"))
            )
            .build();
    }
}
