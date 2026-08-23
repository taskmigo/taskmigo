package io.taskmigo.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "taskmigo.security")
record IdentityProperties(
    @NotBlank String issuer,
    @NotNull Path signingKeyFile,
    @NotBlank String signingKeyId,
    boolean signingKeyAutoCreate,
    @Valid Map<@NotBlank String, @Valid InternalClientDefinition> internalClients,
    @Valid AuthorizationCleanup authorizationCleanup
) {
    IdentityProperties {
        internalClients = Map.copyOf(internalClients);
    }

    record InternalClientDefinition(
        @NotBlank String clientId,
        @Nullable String clientSecret,
        boolean enabled,
        @Min(1) long configurationVersion,
        @NotEmpty Set<@NotBlank String> scopes,
        Set<@NotBlank String> servicePermissions
    ) {
        InternalClientDefinition {
            scopes = Set.copyOf(scopes);
            servicePermissions = Set.copyOf(servicePermissions);
        }
    }

    record AuthorizationCleanup(@NotNull Duration interval, @NotNull Duration retention) {}
}
