package io.taskmigo.audit.context;

import java.util.Objects;

/// Identifies where work executes without conflating that application with the responsible actor.
public record AuditExecutor(Kind kind, String application) {
    public enum Kind {
        WEB,
        WORKER,
        MIGRATION,
        CLI,
        AUTOMATION,
        TEST,
    }

    public AuditExecutor {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(application);
        if (application.isBlank() || application.length() > 100) {
            throw new IllegalArgumentException("Executor application must be 1 to 100 characters");
        }
    }

    public static AuditExecutor web(String application) {
        return new AuditExecutor(Kind.WEB, application);
    }

    public static AuditExecutor worker(String application) {
        return new AuditExecutor(Kind.WORKER, application);
    }

    public static AuditExecutor migration(String application) {
        return new AuditExecutor(Kind.MIGRATION, application);
    }

    public static AuditExecutor test(String application) {
        return new AuditExecutor(Kind.TEST, application);
    }
}
