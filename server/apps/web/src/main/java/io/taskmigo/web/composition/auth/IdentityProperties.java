package io.taskmigo.web.composition.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "taskmigo.oauth")
record IdentityProperties(@NotNull Path signingKeyFile, @NotBlank String signingKeyId, boolean signingKeyAutoCreate) {}
