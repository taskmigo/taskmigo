package io.taskmigo.web.composition.bff;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("taskmigo.bff")
record BffProperties(
    @DefaultValue("") String internalSecret,
    @DefaultValue("60000") long refreshLeaseMilliseconds
) {
    BffProperties {
        if (refreshLeaseMilliseconds <= 0) {
            throw new IllegalArgumentException("BFF refresh lease must be positive");
        }
    }
}
