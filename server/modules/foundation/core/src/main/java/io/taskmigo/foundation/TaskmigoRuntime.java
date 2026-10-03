package io.taskmigo.foundation;

import java.util.TimeZone;

/// Establishes non-configurable process-wide runtime invariants before an executable Taskmigo application boots.
///
/// Taskmigo treats UTC as an application invariant rather than an operator-selectable setting. Initialization is
/// idempotent so every executable may enforce the invariant immediately before handing control to its framework.
public final class TaskmigoRuntime {

    private static final String UTC = "UTC";

    private TaskmigoRuntime() {}

    /// Forces both the declared and effective JVM default timezone to UTC.
    public static void initialize() {
        System.setProperty("user.timezone", UTC);
        TimeZone.setDefault(TimeZone.getTimeZone(UTC));
    }
}
