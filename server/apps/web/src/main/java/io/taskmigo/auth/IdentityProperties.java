package io.taskmigo.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "taskmigo.security")
record IdentityProperties(@NotNull Path signingKeyFile, @NotBlank String signingKeyId, boolean signingKeyAutoCreate) {}
