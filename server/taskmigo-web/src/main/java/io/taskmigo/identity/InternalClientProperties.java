package io.taskmigo.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "taskmigo.internal")
record InternalClientProperties(@NotEmpty List<@Valid Definition> clients) {
    record Definition(
        @NotBlank String id,
        @NotBlank String secret,
        @NotNull Boolean enabled,
        @NotNull @Min(1) Long generation
    ) {}
}
