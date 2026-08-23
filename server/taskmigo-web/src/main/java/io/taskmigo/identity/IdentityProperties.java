package io.taskmigo.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "taskmigo.security")
record IdentityProperties(
    @NotBlank String issuer,
    @NotNull Path signingKeyFile,
    @NotBlank String signingKeyId,
    boolean signingKeyAutoCreate,
    @NotNull AuthorizationCleanup authorizationCleanup
) {
    record AuthorizationCleanup(@NotNull Duration interval, @NotNull Duration retention) {}
}
