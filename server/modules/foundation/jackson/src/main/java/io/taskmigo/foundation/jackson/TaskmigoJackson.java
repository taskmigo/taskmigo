package io.taskmigo.foundation.jackson;

import java.util.Objects;
import java.util.TimeZone;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.MapperBuilder;

/// Applies Taskmigo's shared Jackson builder policy without owning a mapper lifecycle.
public final class TaskmigoJackson {

    private static final String UTC = "UTC";

    private TaskmigoJackson() {}

    /// Applies Taskmigo's non-configurable serialization defaults to the supplied mapper builder.
    ///
    /// @param builder the purpose-specific mapper builder to configure
    /// @return the same builder for normal fluent configuration
    // Jackson 3's MapperBuilder timezone API still requires java.util.TimeZone.
    public static <M extends ObjectMapper, B extends MapperBuilder<M, B>> B configure(B builder) {
        return Objects.requireNonNull(builder, "builder").defaultTimeZone(TimeZone.getTimeZone(UTC));
    }
}
