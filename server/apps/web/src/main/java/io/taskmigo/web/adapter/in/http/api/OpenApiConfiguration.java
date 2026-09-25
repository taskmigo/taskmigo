package io.taskmigo.web.adapter.in.http.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.OAuthScope;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(security = @SecurityRequirement(name = "taskmigoOAuth"))
@SecurityScheme(
    name = "taskmigoOAuth",
    type = SecuritySchemeType.OAUTH2,
    flows = @OAuthFlows(
        authorizationCode = @OAuthFlow(
            authorizationUrl = "/oauth2/authorize",
            tokenUrl = "/oauth2/token",
            scopes = {
                @OAuthScope(name = "openid", description = "OpenID Connect scope"),
                @OAuthScope(name = "profile", description = "Access the user's profile information"),
            }
        )
    )
)
class OpenApiConfiguration {

    @Bean
    GlobalOpenApiCustomizer v0TransportResponses() {
        return openApi ->
            openApi
                .getPaths()
                .forEach((path, pathItem) -> {
                    if (!path.startsWith("/api/v0/")) {
                        return;
                    }
                    pathItem.readOperations().forEach(OpenApiConfiguration::addTransportResponses);
                });
    }

    private static void addTransportResponses(Operation operation) {
        operation
            .getResponses()
            .putIfAbsent("401", new ApiResponse().description("Unauthorized"));
        operation
            .getResponses()
            .putIfAbsent("406", new ApiResponse().description("Not Acceptable"));
        if (operation.getRequestBody() != null) {
            operation
                .getResponses()
                .putIfAbsent("415", new ApiResponse().description("Unsupported Media Type"));
        }
    }
}
