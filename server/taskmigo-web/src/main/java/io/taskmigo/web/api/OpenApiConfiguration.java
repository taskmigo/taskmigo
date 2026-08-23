package io.taskmigo.web.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.OAuthScope;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(title = "Taskmigo API", version = "v0", description = "Taskmigo resource and project management API"),
    security = @SecurityRequirement(name = "taskmigoOAuth")
)
@SecurityScheme(
    name = "taskmigoOAuth",
    type = SecuritySchemeType.OAUTH2,
    flows = @OAuthFlows(
        clientCredentials = @OAuthFlow(
            tokenUrl = "/oauth2/token",
            scopes = @OAuthScope(name = "taskmigo.api", description = "Access the Taskmigo API")
        )
    )
)
class OpenApiConfiguration {}
