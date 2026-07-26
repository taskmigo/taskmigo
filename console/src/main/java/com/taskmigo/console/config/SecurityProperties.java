package com.taskmigo.console.config;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @NotNull URI issuer,
        @NotNull @Valid Bootstrap bootstrap) {

    public record Bootstrap(
            @NotBlank String username,
            @NotBlank String password) {
    }
}
