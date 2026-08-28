package io.taskmigo.identity;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taskmigo.security.bootstrap-user")
record BootstrapUserProperties(@Nullable String password) {}
